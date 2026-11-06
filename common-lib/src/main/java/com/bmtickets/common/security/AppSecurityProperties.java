package com.bmtickets.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Per-service security knobs (12-factor: all supplied through environment).
 *
 * @param publicPaths       paths open to anonymous callers for any HTTP method (e.g. payment webhooks)
 * @param publicGetPaths    paths open to anonymous GET (e.g. catalog browsing)
 * @param internalAuthority authority required on {@code /internal/**} (service-to-service, OAuth2 client-credentials scope)
 */
@ConfigurationProperties("app.security")
public record AppSecurityProperties(
        @DefaultValue List<String> publicPaths,
        @DefaultValue List<String> publicGetPaths,
        @DefaultValue("SCOPE_internal") String internalAuthority) {
}
