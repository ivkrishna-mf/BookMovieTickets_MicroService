package com.bmtickets.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Token-bucket settings per route class. replenishRate = sustained requests/second per key,
 * burstCapacity = bucket size (short spikes), requestedTokens = cost of one request.
 * Defaults live in application.yml; all values are overridable via environment variables (12-factor), e.g. APP_RATELIMIT_BOOKING_REPLENISHRATE=3.
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        Bucket standard,     // authenticated APIs, per user
        Bucket booking,      // booking writes, per user (anti-bot / anti-hoarding)
        Bucket catalog,      // anonymous browsing, per client IP
        Bucket webhook,      // payment provider callbacks, per source IP
        @DefaultValue("1") int trustedProxyHops,      // ALB => 1 hop appended to X-Forwarded-For
        @DefaultValue("2000") int maxInFlightRequests // load shedding threshold per gateway pod
) {
    public record Bucket(int replenishRate, int burstCapacity, int requestedTokens) {
    }
}
