package com.bmtickets.inventory.hold;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * First line of defence against concurrent seat selection: an atomic Redis "distributed lock per seat".
 * Thousands of users clicking the same seat are rejected in ~1ms by Redis instead of queueing on database row locks.
 * Redis is NOT the source of truth (replication is async; a failover can lose a key) - the DB check is.
 */
@Component
public class SeatHoldCache {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldCache.class);

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> holdScript = new DefaultRedisScript<>();
    private final DefaultRedisScript<Long> releaseScript = new DefaultRedisScript<>();

    public SeatHoldCache(StringRedisTemplate redis) {
        this.redis = redis;
        holdScript.setLocation(new ClassPathResource("scripts/hold_seats.lua"));
        holdScript.setResultType(List.class);
        releaseScript.setLocation(new ClassPathResource("scripts/release_seats.lua"));
        releaseScript.setResultType(Long.class);
    }

    /** @return seat numbers already held by someone else (empty = all seats now held by {@code holdRef}). */
    @SuppressWarnings("unchecked")
    public List<String> tryHold(UUID showId, Collection<String> seatNumbers, UUID holdRef, Duration ttl) {
        try {
            List<Object> conflicts = redis.execute(holdScript, keys(showId, seatNumbers),
                    holdRef.toString(), String.valueOf(ttl.toMillis()));
            if (conflicts == null) {
                return List.of();
            }
            return conflicts.stream().map(String::valueOf).map(k -> k.substring(k.lastIndexOf(':') + 1)).toList();
        } catch (RuntimeException e) {
            // Redis unavailable: degrade gracefully, the database lock still guarantees correctness.
            log.warn("Seat hold cache unavailable, falling back to DB-only locking: {}", e.getMessage());
            return List.of();
        }
    }

    public void release(UUID showId, Collection<String> seatNumbers, UUID holdRef) {
        if (seatNumbers.isEmpty()) {
            return;
        }
        try {
            redis.execute(releaseScript, keys(showId, seatNumbers), holdRef.toString());
        } catch (RuntimeException e) {
            log.warn("Could not release Redis holds for {} (they will expire by TTL): {}", holdRef, e.getMessage());
        }
    }

    private static List<String> keys(UUID showId, Collection<String> seatNumbers) {
        return seatNumbers.stream().map(n -> "seat-hold:{" + showId + "}:" + n).toList();
    }
}
