package com.bmtickets.grpc.security;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Attaches a (cached, auto-refreshed) service access token to every outgoing gRPC call. */
public class BearerTokenCallCredentials extends CallCredentials {

    private final Supplier<String> tokenSupplier;

    public BearerTokenCallCredentials(Supplier<String> tokenSupplier) {
        this.tokenSupplier = tokenSupplier;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
        appExecutor.execute(() -> {
            try {
                Metadata headers = new Metadata();
                headers.put(GrpcJwtServerInterceptor.AUTHORIZATION, "Bearer " + tokenSupplier.get());
                applier.apply(headers);
            } catch (RuntimeException e) {
                applier.fail(Status.UNAUTHENTICATED.withDescription("Cannot obtain service token").withCause(e));
            }
        });
    }
}
