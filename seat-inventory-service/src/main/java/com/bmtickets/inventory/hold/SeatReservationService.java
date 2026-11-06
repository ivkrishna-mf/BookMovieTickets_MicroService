package com.bmtickets.inventory.hold;

import com.bmtickets.common.ratelimit.BucketSpec;
import com.bmtickets.common.ratelimit.DistributedRateLimiter;
import com.bmtickets.inventory.api.SeatMapCache;
import com.bmtickets.inventory.config.InventoryProperties;
import com.bmtickets.inventory.domain.Seat;
import com.bmtickets.inventory.domain.SeatRepository;
import com.bmtickets.inventory.domain.SeatStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Seat reservation with layered concurrency control:
 * <ol>
 *   <li><b>Admission throttle</b> per show (distributed token bucket) - protects Redis/DB during flash sales.</li>
 *   <li><b>Redis atomic hold</b> (Lua, all-or-nothing) - fast rejection of contended seats.</li>
 *   <li><b>DB pessimistic lock in deterministic order + state check + @Version</b> - the source of truth.</li>
 * </ol>
 * Every operation is keyed by {@code holdRef} (= booking id) and is idempotent, so the saga can retry safely.
 */
@Service
public class SeatReservationService {

    private final SeatRepository seats;
    private final SeatHoldCache holdCache;
    private final SeatMapCache seatMapCache;
    private final DistributedRateLimiter rateLimiter;
    private final TransactionTemplate tx;
    private final InventoryProperties props;
    private final MeterRegistry metrics;

    public SeatReservationService(SeatRepository seats, SeatHoldCache holdCache, SeatMapCache seatMapCache,
                                  DistributedRateLimiter rateLimiter, TransactionTemplate tx,
                                  InventoryProperties props, MeterRegistry metrics) {
        this.seats = seats;
        this.holdCache = holdCache;
        this.seatMapCache = seatMapCache;
        this.rateLimiter = rateLimiter;
        this.tx = tx;
        this.props = props;
        this.metrics = metrics;
    }

    public HoldResult hold(UUID showId, List<String> requested, UUID holdRef, Duration requestedTtl) {
        Set<String> seatNumbers = new TreeSet<>(requested);            // de-duplicate + stable order
        Duration ttl = requestedTtl.compareTo(props.maxHoldTtl()) > 0 ? props.maxHoldTtl() : requestedTtl;

        // 1. admission throttle for this show (shared by all pods)
        var decision = rateLimiter.tryAcquire("show-hold:" + showId,
                new BucketSpec(props.showHoldBurst(), props.showHoldRatePerSec()));
        if (!decision.allowed()) {
            metrics.counter("inventory.hold.throttled").increment();
            return HoldResult.failed("THROTTLED", List.of());
        }

        // 2. Redis fast path
        List<String> redisConflicts = holdCache.tryHold(showId, seatNumbers, holdRef, ttl);
        if (!redisConflicts.isEmpty()) {
            metrics.counter("inventory.hold.conflict", "layer", "redis").increment();
            return HoldResult.failed("SEATS_UNAVAILABLE", redisConflicts);
        }

        // 3. database source of truth
        HoldResult result;
        try {
            result = tx.execute(status -> {
                Instant now = Instant.now();
                List<Seat> locked = seats.lockSeats(showId, seatNumbers);
                if (locked.size() != seatNumbers.size()) {
                    Set<String> unknown = new TreeSet<>(seatNumbers);
                    locked.forEach(s -> unknown.remove(s.getSeatNumber()));
                    return HoldResult.failed("UNKNOWN_SEATS", List.copyOf(unknown));
                }
                List<String> taken = locked.stream().filter(s -> !s.isHoldableBy(holdRef, now))
                        .map(Seat::getSeatNumber).toList();
                if (!taken.isEmpty()) {
                    return HoldResult.failed("SEATS_UNAVAILABLE", taken);
                }
                Instant expiresAt = now.plus(ttl);
                Map<String, String> categories = new LinkedHashMap<>();
                for (Seat s : locked) {
                    s.hold(holdRef, expiresAt);
                    categories.put(s.getSeatNumber(), s.getCategory());
                }
                return HoldResult.ok(categories, expiresAt);
            });
        } catch (RuntimeException e) {
            holdCache.release(showId, seatNumbers, holdRef);
            throw e;
        }
        if (result == null || !result.success()) {
            holdCache.release(showId, seatNumbers, holdRef);
            metrics.counter("inventory.hold.conflict", "layer", "db").increment();
        } else {
            seatMapCache.evict(showId);
            metrics.counter("inventory.hold.success").increment();
        }
        return result;
    }

    /** HELD -> BOOKED for every seat of the hold. Idempotent: already BOOKED by the same ref counts as success. */
    public int confirm(UUID holdRef) {
        List<Seat> confirmed = tx.execute(status -> {
            List<Seat> held = seats.lockByHoldRef(holdRef);
            if (held.isEmpty()) {
                return List.<Seat>of();
            }
            held.stream().filter(s -> s.getStatus() == SeatStatus.HELD).forEach(Seat::book);
            return held;
        });
        if (confirmed == null || confirmed.isEmpty()) {
            return 0;
        }
        UUID showId = confirmed.get(0).getShowId();
        holdCache.release(showId, confirmed.stream().map(Seat::getSeatNumber).toList(), holdRef);
        seatMapCache.evict(showId);
        return confirmed.size();
    }

    /** HELD -> AVAILABLE (saga compensation). Idempotent: releasing an unknown/finished hold affects 0 seats. */
    public int release(UUID holdRef) {
        List<Seat> released = tx.execute(status -> {
            List<Seat> held = seats.lockByHoldRef(holdRef).stream()
                    .filter(s -> s.getStatus() == SeatStatus.HELD).toList();
            held.forEach(Seat::release);
            return held;
        });
        if (released == null || released.isEmpty()) {
            return 0;
        }
        UUID showId = released.get(0).getShowId();
        holdCache.release(showId, released.stream().map(Seat::getSeatNumber).toList(), holdRef);
        seatMapCache.evict(showId);
        return released.size();
    }
}
