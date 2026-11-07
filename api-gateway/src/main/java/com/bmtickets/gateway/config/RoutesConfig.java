package com.bmtickets.gateway.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.filter.factory.RequestSizeGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

/**
 * Routes are declared in code so every route explicitly states its rate-limit key, bucket, circuit breaker
 * and retry policy. "lb://" URIs are resolved by Spring Cloud LoadBalancer through the active DiscoveryClient
 * (Eureka locally, Kubernetes Services on EKS).
 */
@Configuration
public class RoutesConfig {

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder,
                        @Qualifier("userKeyResolver") KeyResolver userKey,
                        @Qualifier("ipKeyResolver") KeyResolver ipKey,
                        @Qualifier("standardRateLimiter") RedisRateLimiter standard,
                        @Qualifier("bookingRateLimiter") RedisRateLimiter booking,
                        @Qualifier("catalogRateLimiter") RedisRateLimiter catalog,
                        @Qualifier("webhookRateLimiter") RedisRateLimiter webhook,
                        RequestSizeGatewayFilterFactory requestSizeFilterFactory) {
        return builder.routes()
                // ---- read-heavy, public, cacheable ----
                .route("catalog", r -> r.path("/api/v1/catalog/**")
                        .filters(f -> f
                                .requestRateLimiter(c -> c.setRateLimiter(catalog).setKeyResolver(ipKey))
                                .circuitBreaker(c -> c.setName("catalog").setFallbackUri("forward:/fallback/catalog"))
                                .retry(c -> c.setRetries(2).setMethods(HttpMethod.GET)
                                        .setStatuses(HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE)))
                        .uri("lb://catalog-service"))

                .route("seat-map", r -> r.path("/api/v1/shows/*/seats").and().method(HttpMethod.GET)
                        .filters(f -> f
                                .requestRateLimiter(c -> c.setRateLimiter(catalog).setKeyResolver(ipKey))
                                .circuitBreaker(c -> c.setName("seat-inventory").setFallbackUri("forward:/fallback/seat-inventory")))
                        .uri("lb://seat-inventory-service"))

                // ---- booking writes: strict per-user bucket (anti-bot, anti seat-hoarding) ----
                .route("bookings", r -> r.path("/api/v1/bookings/**")
                        .filters(f -> f
                                .requestRateLimiter(c -> c.setRateLimiter(booking).setKeyResolver(userKey))
                                .filter(requestSizeFilterFactory.apply(
                                        new RequestSizeGatewayFilterFactory.RequestSizeConfig()
                                                .setMaxSize(DataSize.ofKilobytes(16))))
                                .circuitBreaker(c -> c.setName("booking").setFallbackUri("forward:/fallback/booking")))
                        .uri("lb://booking-service"))

                // ---- payment provider webhooks: unauthenticated (signature verified downstream), per-IP limit ----
                .route("payment-webhooks", r -> r.path("/api/v1/payments/webhooks/**")
                        .filters(f -> f
                                .requestRateLimiter(c -> c.setRateLimiter(webhook).setKeyResolver(ipKey))
                                .filter(requestSizeFilterFactory.apply(
                                        new RequestSizeGatewayFilterFactory.RequestSizeConfig()
                                                .setMaxSize(DataSize.ofKilobytes(256)))))
                        .uri("lb://payment-service"))

                .route("payments", r -> r.path("/api/v1/payments/**")
                        .filters(f -> f
                                .requestRateLimiter(c -> c.setRateLimiter(standard).setKeyResolver(userKey))
                                .circuitBreaker(c -> c.setName("payment").setFallbackUri("forward:/fallback/payment")))
                        .uri("lb://payment-service"))
                .build();
    }
}
