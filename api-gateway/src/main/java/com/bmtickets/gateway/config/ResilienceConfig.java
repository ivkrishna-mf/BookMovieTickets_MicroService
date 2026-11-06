package com.bmtickets.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.netty.channel.ChannelOption;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    /**
     * Default circuit breaker: opens when >=50% of the last 50 calls fail or are slower than 5s,
     * stays open 15s, then lets 10 trial calls through (half-open).
     * The time limiter is generous because a booking call runs a multi-step saga.
     */
    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCircuitBreaker() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(50)
                        .minimumNumberOfCalls(20)
                        .failureRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofSeconds(5))
                        .slowCallRateThreshold(80)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .permittedNumberOfCallsInHalfOpenState(10)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(15)).build())
                .build());
    }

    /** Network timeouts on the proxied connection pool: never let a slow upstream tie up gateway resources. */
    @Bean
    HttpClientCustomizer httpClientTimeouts() {
        return client -> client
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .responseTimeout(Duration.ofSeconds(20));
    }
}
