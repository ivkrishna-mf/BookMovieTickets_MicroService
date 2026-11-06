package com.bmtickets.inventory.jobs;

import com.bmtickets.inventory.domain.Seat;
import com.bmtickets.inventory.domain.SeatRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Safety net: returns abandoned holds to the pool (e.g. user closed the tab and the booking-service expiry
 * also failed). ShedLock guarantees only one replica runs it at a time.
 */
@Component
public class HoldExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(HoldExpirySweeper.class);
    private final SeatRepository seats;

    public HoldExpirySweeper(SeatRepository seats) {
        this.seats = seats;
    }

    @Scheduled(fixedDelayString = "${app.inventory.sweep-interval-ms:30000}")
    @SchedulerLock(name = "seat-hold-expiry-sweeper", lockAtMostFor = "PT2M", lockAtLeastFor = "PT10S")
    @Transactional
    public void releaseExpiredHolds() {
        Instant now = Instant.now();
        List<Seat> expired = seats.findExpiredHolds(now, PageRequest.of(0, 1000));
        if (expired.isEmpty()) {
            return;
        }
        int released = seats.releaseExpired(expired.stream().map(Seat::getId).toList(), now);
        log.info("Released {} expired seat holds", released);
    }
}
