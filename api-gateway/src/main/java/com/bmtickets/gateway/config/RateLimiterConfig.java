package com.bmtickets.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;

/**
 * Distributed rate limiting at the edge using Spring Cloud Gateway's RedisRateLimiter
 * (token bucket executed atomically by a Lua script in ElastiCache Redis, shared by all gateway pods).
 */
@Configuration
public class RateLimiterConfig {

    /** Authenticated callers are limited per user (JWT sub); falls back to client IP for anonymous calls. */
    @Bean
    @Primary
    KeyResolver userKeyResolver(RemoteAddressResolver remoteAddressResolver) {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(sub -> "user:" + sub)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + clientIp(exchange, remoteAddressResolver)));
    }

    @Bean
    KeyResolver ipKeyResolver(RemoteAddressResolver remoteAddressResolver) {
        return exchange -> Mono.just("ip:" + clientIp(exchange, remoteAddressResolver));
    }

    /**
     * Only trust the X-Forwarded-For entries appended by our own proxies (ALB), never the left-most
     * client-supplied value. With no trusted proxy, use the socket peer address.
     */
    @Bean
    RemoteAddressResolver remoteAddressResolver(RateLimitProperties props) {
        int trustedProxyHops = props.trustedProxyHops();
        if (trustedProxyHops < 0) {
            throw new IllegalArgumentException("trustedProxyHops must not be negative");
        }
        if (trustedProxyHops == 0) {
            return new RemoteAddressResolver() {
                @Override
                public InetSocketAddress resolve(ServerWebExchange exchange) {
                    return exchange.getRequest().getRemoteAddress();
                }
            };
        }
        return XForwardedRemoteAddressResolver.maxTrustedIndex(trustedProxyHops);
    }

    @Bean
    @Primary
    RedisRateLimiter standardRateLimiter(RateLimitProperties p) {
        return limiter(p.standard());
    }

    @Bean
    RedisRateLimiter bookingRateLimiter(RateLimitProperties p) {
        return limiter(p.booking());
    }

    @Bean
    RedisRateLimiter catalogRateLimiter(RateLimitProperties p) {
        return limiter(p.catalog());
    }

    @Bean
    RedisRateLimiter webhookRateLimiter(RateLimitProperties p) {
        return limiter(p.webhook());
    }

    private static RedisRateLimiter limiter(RateLimitProperties.Bucket b) {
        return new RedisRateLimiter(b.replenishRate(), b.burstCapacity(), b.requestedTokens());
    }

    private static String clientIp(ServerWebExchange exchange, RemoteAddressResolver resolver) {
        InetSocketAddress address = resolver.resolve(exchange);
        if (address == null) {
            return "unknown";
        }
        return address.getAddress() != null ? address.getAddress().getHostAddress() : address.getHostString();
    }
}
