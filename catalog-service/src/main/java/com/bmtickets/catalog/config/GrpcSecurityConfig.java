package com.bmtickets.catalog.config;

import com.bmtickets.common.security.JwtRoleConverter;
import com.bmtickets.grpc.security.GrpcJwtServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** gRPC endpoints accept only internal service identities (same rule as REST /internal/**). */
@Configuration(proxyBeanMethods = false)
public class GrpcSecurityConfig {

    @GrpcGlobalServerInterceptor
    GrpcJwtServerInterceptor grpcJwtServerInterceptor(JwtDecoder jwtDecoder,
                                                      @Value("${app.security.internal-authority:SCOPE_internal}") String authority,
                                                      @Value("${app.grpc.auth-enabled:true}") boolean enabled) {
        JwtRoleConverter converter = new JwtRoleConverter();
        return new GrpcJwtServerInterceptor(jwtDecoder,
                jwt -> converter.convert(jwt).getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority())),
                enabled);
    }
}
