package com.bmtickets.grpc.security;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.function.Predicate;

/**
 * Authenticates internal gRPC calls with an OAuth2 client-credentials access token (same IdP as REST) and
 * authorizes them with a pluggable rule (e.g. "has SCOPE_bmtickets/internal" for Cognito, "has ROLE_SERVICE" for
 * Keycloak). Defence in depth on top of Kubernetes NetworkPolicies.
 */
public class GrpcJwtServerInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final JwtDecoder jwtDecoder;
    private final Predicate<Jwt> authorizer;
    private final boolean enabled;

    public GrpcJwtServerInterceptor(JwtDecoder jwtDecoder, Predicate<Jwt> authorizer, boolean enabled) {
        this.jwtDecoder = jwtDecoder;
        this.authorizer = authorizer;
        this.enabled = enabled;
    }

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                                                       ServerCallHandler<Q, R> next) {
        String method = call.getMethodDescriptor().getFullMethodName();
        if (!enabled || method.startsWith("grpc.health.") || method.startsWith("grpc.reflection.")) {
            return next.startCall(call, headers);
        }
        String header = headers.get(AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing bearer token"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7));
            if (!authorizer.test(jwt)) {
                call.close(Status.PERMISSION_DENIED.withDescription("Caller is not an internal service"), new Metadata());
                return new ServerCall.Listener<>() { };
            }
        } catch (JwtException e) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid token"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
        return next.startCall(call, headers);
    }
}
