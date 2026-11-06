package com.bmtickets.payment.service;

import com.bmtickets.common.error.BusinessException;
import com.bmtickets.common.events.EventTypes;
import com.bmtickets.common.events.PaymentFailedEvent;
import com.bmtickets.common.events.PaymentRefundedEvent;
import com.bmtickets.common.events.PaymentSucceededEvent;
import com.bmtickets.common.events.Topics;
import com.bmtickets.common.messaging.InboxGuard;
import com.bmtickets.common.messaging.OutboxService;
import com.bmtickets.payment.domain.Payment;
import com.bmtickets.payment.domain.PaymentRepository;
import com.bmtickets.payment.domain.PaymentStatus;
import com.bmtickets.payment.gateway.PaymentGateway;
import com.bmtickets.payment.gateway.PaymentGateway.ProviderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment use cases. PSP calls are made OUTSIDE database transactions (never hold a DB connection while waiting on
 * the network); state changes and their outgoing events are committed atomically through the outbox.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final OutboxService outbox;
    private final InboxGuard inbox;
    private final TransactionTemplate tx;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway, OutboxService outbox, InboxGuard inbox,
                          TransactionTemplate tx) {
        this.payments = payments;
        this.gateway = gateway;
        this.outbox = outbox;
        this.inbox = inbox;
        this.tx = tx;
    }

    public record PaymentView(UUID paymentId, String providerPaymentId, String clientSecret, String status) {
        static PaymentView of(Payment p) {
            return new PaymentView(p.getId(), p.getProviderPaymentId(), p.getClientSecret(), p.getStatus().name());
        }
    }

    /** Idempotent on bookingId: a retry returns the same payment / same PaymentIntent. */
    public PaymentView create(UUID bookingId, BigDecimal amount, String currency, String email) {
        Payment payment = payments.findByBookingId(bookingId).orElseGet(() -> {
            try {
                return tx.execute(s -> payments.saveAndFlush(new Payment(bookingId, amount, currency, email)));
            } catch (DataIntegrityViolationException race) {
                return payments.findByBookingId(bookingId).orElseThrow();
            }
        });
        if (payment.getStatus() != PaymentStatus.CREATING) {
            return PaymentView.of(payment);
        }
        // CREATING: (re)issue the intent; the PSP idempotency key returns the same intent on retries
        PaymentGateway.GatewayIntent intent = gateway.createIntent(bookingId, payment.getAmount(), payment.getCurrency(), email);
        Payment updated = tx.execute(s -> {
            Payment p = payments.findById(payment.getId()).orElseThrow();
            p.attachIntent(intent.providerPaymentId(), intent.clientSecret());
            return p;
        });
        return PaymentView.of(updated);
    }

    /** Called by booking-service on cancel/expiry. Tells the caller if the money was already captured. */
    public String cancel(UUID bookingId) {
        Payment p = payments.findByBookingId(bookingId).orElse(null);
        if (p == null) {
            return "NOT_FOUND";
        }
        if (p.getStatus() == PaymentStatus.SUCCEEDED || p.getStatus() == PaymentStatus.REFUNDED) {
            return "ALREADY_SUCCEEDED";
        }
        if (p.getStatus() == PaymentStatus.CANCELED) {
            return "CANCELED";
        }
        PaymentGateway.CancelOutcome outcome = p.getProviderPaymentId() == null
                ? PaymentGateway.CancelOutcome.CANCELED
                : gateway.cancel(p.getProviderPaymentId());
        tx.executeWithoutResult(s -> {
            Payment fresh = payments.findById(p.getId()).orElseThrow();
            if (outcome == PaymentGateway.CancelOutcome.ALREADY_SUCCEEDED) {
                if (fresh.markSucceeded()) {   // webhook not processed yet: emit now (dedup by state)
                    publishSucceeded(fresh);
                }
            } else {
                fresh.markCanceled();          // initiated by booking: no PaymentFailed event needed
            }
        });
        return outcome == PaymentGateway.CancelOutcome.ALREADY_SUCCEEDED ? "ALREADY_SUCCEEDED" : "CANCELED";
    }

    /** Webhook / simulated provider event. Deduplicated by provider event id (PSPs deliver at-least-once). */
    public void handleProviderEvent(ProviderEvent event) {
        tx.executeWithoutResult(s -> {
            if (!inbox.firstDelivery("psp-webhook", event.id())) {
                return;
            }
            Payment p = payments.findByProviderPaymentId(event.providerPaymentId()).orElse(null);
            if (p == null) {
                log.warn("Provider event {} for unknown payment {}", event.id(), event.providerPaymentId());
                return;
            }
            switch (event.type()) {
                case SUCCEEDED -> {
                    if (p.markSucceeded()) {
                        publishSucceeded(p);
                    }
                }
                case FAILED_ATTEMPT -> p.recordError(event.message());     // user may retry with another card
                case CANCELED -> {
                    if (p.markCanceled()) {
                        outbox.record("Payment", p.getBookingId(), EventTypes.PAYMENT_FAILED, Topics.PAYMENT_EVENTS,
                                new PaymentFailedEvent(p.getId(), p.getBookingId(), "CANCELED"));
                    }
                }
                case REFUNDED -> {
                    if (p.markRefunded(null)) {
                        outbox.record("Payment", p.getBookingId(), EventTypes.PAYMENT_REFUNDED, Topics.PAYMENT_EVENTS,
                                new PaymentRefundedEvent(p.getId(), p.getBookingId(), null));
                    }
                }
            }
        });
    }

    /** Saga compensation requested by booking-service. */
    public void refund(UUID bookingId) {
        Payment p = payments.findByBookingId(bookingId)
                .orElseThrow(() -> BusinessException.notFound("Payment for booking", bookingId));
        if (p.getStatus() == PaymentStatus.REFUNDED || p.getStatus() == PaymentStatus.CANCELED) {
            return;
        }
        if (p.getStatus() != PaymentStatus.SUCCEEDED) {
            cancel(bookingId);
            return;
        }
        String refundId = gateway.refund(p.getProviderPaymentId(), bookingId);   // PSP idempotency key: refund-<booking>
        tx.executeWithoutResult(s -> {
            Payment fresh = payments.findById(p.getId()).orElseThrow();
            if (fresh.markRefunded(refundId)) {
                outbox.record("Payment", fresh.getBookingId(), EventTypes.PAYMENT_REFUNDED, Topics.PAYMENT_EVENTS,
                        new PaymentRefundedEvent(fresh.getId(), fresh.getBookingId(), refundId));
            }
        });
    }

    public PaymentView findByBooking(UUID bookingId) {
        return payments.findByBookingId(bookingId).map(PaymentView::of)
                .orElseThrow(() -> BusinessException.notFound("Payment for booking", bookingId));
    }

    private void publishSucceeded(Payment p) {
        outbox.record("Payment", p.getBookingId(), EventTypes.PAYMENT_SUCCEEDED, Topics.PAYMENT_EVENTS,
                new PaymentSucceededEvent(p.getId(), p.getBookingId(), p.getProviderPaymentId(), p.getAmount(), p.getCurrency()));
    }
}
