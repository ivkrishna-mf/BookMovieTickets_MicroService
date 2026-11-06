package com.bmtickets.notification.messaging;

import com.bmtickets.common.ratelimit.BucketSpec;
import com.bmtickets.common.ratelimit.DistributedRateLimiter;
import com.bmtickets.notification.domain.NotificationLog;
import com.bmtickets.notification.domain.NotificationLogRepository;
import com.bmtickets.notification.email.EmailSender;
import com.bmtickets.notification.email.TicketEmailRenderer.RenderedEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.UUID;

/**
 * Sends each notification once (unique booking+type), throttled to the SES account send rate shared by all pods.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationLogRepository logs;
    private final EmailSender sender;
    private final DistributedRateLimiter limiter;
    private final TransactionTemplate tx;
    private final BucketSpec sesBucket;

    public NotificationDispatcher(NotificationLogRepository logs, EmailSender sender, DistributedRateLimiter limiter,
                                  TransactionTemplate tx,
                                  @Value("${app.notification.send-rate-per-sec:14}") double sendRate) {
        this.logs = logs;
        this.sender = sender;
        this.limiter = limiter;
        this.tx = tx;
        this.sesBucket = new BucketSpec(Math.max(1, (long) sendRate), sendRate);
    }

    public void sendOnce(UUID bookingId, String type, String recipient, RenderedEmail email) {
        if (recipient == null || recipient.isBlank()) {
            log.warn("No recipient for {} {}, skipping", type, bookingId);
            return;
        }
        NotificationLog entry = logs.findByBookingIdAndType(bookingId, type).orElseGet(() -> {
            try {
                return tx.execute(s -> logs.saveAndFlush(new NotificationLog(bookingId, type, recipient)));
            } catch (DataIntegrityViolationException race) {
                return logs.findByBookingIdAndType(bookingId, type).orElseThrow();
            }
        });
        if ("SENT".equals(entry.getStatus())) {
            return;   // duplicate event delivery
        }
        // Throttling: wait for an SES send token; if none within 10s, throw => non-blocking retry topic.
        if (!limiter.acquire("ses-send", sesBucket, Duration.ofSeconds(10))) {
            throw new IllegalStateException("SES send budget exhausted, retrying later");
        }
        String messageId = sender.send(recipient, email.subject(), email.html(), email.text());
        tx.executeWithoutResult(s -> logs.findById(entry.getId()).ifPresent(l -> l.markSent(messageId)));
    }
}
