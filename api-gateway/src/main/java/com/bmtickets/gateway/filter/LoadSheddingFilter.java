package com.bmtickets.gateway.filter;

import com.bmtickets.gateway.config.RateLimitProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Throttling by concurrency (load shedding). Rate limits cap requests per key over time; this caps the number of
 * requests a single gateway pod processes at once. When upstreams slow down, in-flight requests pile up; past the
 * threshold we fail fast with 503 + Retry-After rather than exhausting memory/connections, and the HPA scales out.
 */
@Component
public class LoadSheddingFilter implements GlobalFilter, Ordered {

    private final Semaphore permits;
    private final Counter shed;

    public LoadSheddingFilter(RateLimitProperties props, MeterRegistry registry) {
        int max = props.maxInFlightRequests();
        this.permits = new Semaphore(max);
        this.shed = Counter.builder("gateway.load_shed.total").description("Requests rejected by load shedding").register(registry);
        Gauge.builder("gateway.inflight.requests", permits, p -> max - p.availablePermits()).register(registry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!permits.tryAcquire()) {
            shed.increment();
            exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            exchange.getResponse().getHeaders().set("Retry-After", "1");
            return exchange.getResponse().setComplete();
        }
        AtomicBoolean released = new AtomicBoolean();
        return chain.filter(exchange).doFinally(signal -> {
            if (released.compareAndSet(false, true)) {
                permits.release();
            }
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
