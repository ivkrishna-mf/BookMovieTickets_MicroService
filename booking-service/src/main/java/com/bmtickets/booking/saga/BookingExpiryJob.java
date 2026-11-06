package com.bmtickets.booking.saga;

import com.bmtickets.booking.domain.BookingRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Saga timeout handling: expire unpaid bookings and give the seats back. One replica at a time (ShedLock). */
@Component
public class BookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

    private final BookingRepository bookings;
    private final BookingCompletionService completion;

    public BookingExpiryJob(BookingRepository bookings, BookingCompletionService completion) {
        this.bookings = bookings;
        this.completion = completion;
    }

    @Scheduled(fixedDelayString = "${app.booking.expiry-scan-interval-ms:15000}")
    @SchedulerLock(name = "booking-expiry", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void expireUnpaidBookings() {
        Instant now = Instant.now();
        List<UUID> ids = bookings.findExpiredIds(now, now.minus(Duration.ofMinutes(2)), PageRequest.of(0, 200));
        for (UUID id : ids) {
            try {
                completion.expire(id);
            } catch (RuntimeException e) {
                log.warn("Expiry of booking {} failed, will retry next run: {}", id, e.toString());
            }
        }
    }
}
