package com.bmtickets.common.ratelimit;

import com.bmtickets.common.error.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Cluster-wide token bucket backed by Redis (ElastiCache) and an atomic Lua script.
 * <p>
 * Unlike Resilience4j's RateLimiter (per JVM), the budget here is shared by ALL replicas, which is what you need
 * to respect an external quota (Stripe API, SES send rate) or protect a hot resource (one show during a flash sale)
 * regardless of how many pods the HPA has started.
 * <ul>
 *   <li>{@link #tryAcquire} - <b>rate limiting</b>: reject immediately when the budget is spent.</li>
 *   <li>{@link #acquire} - <b>throttling</b>: wait (bounded) until a token is available, smoothing bursts.</li>
 * </ul>
 * Fails open if Redis is unreachable: availability of booking is preferred over strict limiting, and the
 * per-instance Resilience4j limiters/bulkheads still apply as a second line of defence.
 */
@Component
public class DistributedRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(DistributedRateLimiter.class);
    private static final String PREFIX = "rl:";

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> script;

    @SuppressWarnings("rawtypes")
    public DistributedRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        this.script.setResultType(List.class);
    }

    public RateLimitDecision tryAcquire(String key, BucketSpec spec) {
        return tryAcquire(key, spec, 1);
    }

    @SuppressWarnings("unchecked")
    public RateLimitDecision tryAcquire(String key, BucketSpec spec, int permits) {
        try {
            // "{key}" hash tag keeps the single key routable in Redis Cluster mode
            List<Long> r = redis.execute(script, List.of(PREFIX + "{" + key + "}"),
                    String.valueOf(spec.capacity()), String.valueOf(spec.refillPerSecond()), String.valueOf(permits));
            if (r == null || r.size() < 3) {
                return new RateLimitDecision(true, -1, 0);
            }
            return new RateLimitDecision(r.get(0) == 1L, r.get(1), r.get(2));
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable for key {} - failing open: {}", key, e.getMessage());
            return new RateLimitDecision(true, -1, 0);
        }
    }

    /** Rate limiting: throws {@link TooManyRequestsException} (HTTP 429 + Retry-After) when over budget. */
    public void checkOrThrow(String key, BucketSpec spec, String message) {
        RateLimitDecision d = tryAcquire(key, spec);
        if (!d.allowed()) {
            throw new TooManyRequestsException(message, (long) Math.ceil(d.retryAfterMillis() / 1000.0));
        }
    }

    /**
     * Throttling: blocks the caller until a permit is granted or {@code maxWait} elapses.
     * Cheap on Java 21 virtual threads (spring.threads.virtual.enabled=true): a parked virtual thread holds no OS thread.
     *
     * @return true if a permit was obtained
     */
    public boolean acquire(String key, BucketSpec spec, Duration maxWait) {
        long deadline = System.nanoTime() + maxWait.toNanos();
        while (true) {
            RateLimitDecision d = tryAcquire(key, spec);
            if (d.allowed()) {
                return true;
            }
            long remainingMs = (deadline - System.nanoTime()) / 1_000_000;
            if (remainingMs <= 0) {
                return false;
            }
            try {
                Thread.sleep(Math.max(5, Math.min(d.retryAfterMillis(), remainingMs)));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
