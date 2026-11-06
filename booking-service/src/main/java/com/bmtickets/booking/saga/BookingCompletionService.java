package com.bmtickets.booking.saga;

import com.bmtickets.booking.client.InventoryClient;
import com.bmtickets.booking.client.PaymentClient;
import com.bmtickets.booking.client.PaymentServiceAdapter;
import com.bmtickets.booking.domain.Booking;
import com.bmtickets.booking.domain.BookingRepository;
import com.bmtickets.booking.domain.BookingStatus;
import com.bmtickets.booking.ticket.TicketCodeGenerator;
import com.bmtickets.common.events.BookingCancelledEvent;
import com.bmtickets.common.events.BookingConfirmedEvent;
import com.bmtickets.common.events.EventTypes;
import com.bmtickets.common.events.RefundRequestedCommand;
import com.bmtickets.common.events.Topics;
import com.bmtickets.common.messaging.InboxGuard;
import com.bmtickets.common.messaging.OutboxService;
import com.bmtickets.grpc.inventory.v1.SeatOperationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Asynchronous continuation of the booking saga, driven by payment events.
 * Remote calls happen outside DB transactions; every state change + outgoing event + inbox marker commit together.
 */
@Service
public class BookingCompletionService {

    private static final Logger log = LoggerFactory.getLogger(BookingCompletionService.class);
    private static final String CONSUMER = "booking-saga";

    private final BookingRepository bookings;
    private final InventoryClient inventory;
    private final PaymentServiceAdapter payments;
    private final OutboxService outbox;
    private final InboxGuard inbox;
    private final TicketCodeGenerator tickets;
    private final TransactionTemplate tx;

    public BookingCompletionService(BookingRepository bookings, InventoryClient inventory, PaymentServiceAdapter payments,
                                    OutboxService outbox, InboxGuard inbox, TicketCodeGenerator tickets,
                                    TransactionTemplate tx) {
        this.bookings = bookings;
        this.inventory = inventory;
        this.payments = payments;
        this.outbox = outbox;
        this.inbox = inbox;
        this.tickets = tickets;
        this.tx = tx;
    }

    /** Payment captured: confirm seats, confirm booking, emit BookingConfirmed (-> ticket e-mail). */
    public void onPaymentSucceeded(String eventId, UUID bookingId) {
        Booking current = bookings.findById(bookingId).orElse(null);
        if (current == null) {
            log.error("PaymentSucceeded for unknown booking {}", bookingId);
            return;
        }
        if (current.getStatus() == BookingStatus.CONFIRMED) {
            return;                                                    // duplicate delivery
        }
        if (current.getStatus() != BookingStatus.PENDING_PAYMENT) {
            // late payment for an expired/cancelled booking => compensate with a refund
            tx.executeWithoutResult(s -> {
                if (inbox.firstDelivery(CONSUMER, eventId)) {
                    outbox.record("Booking", bookingId, EventTypes.REFUND_REQUESTED, Topics.PAYMENT_COMMANDS,
                            new RefundRequestedCommand(bookingId, "BOOKING_" + current.getStatus()));
                }
            });
            return;
        }

        SeatOperationResponse confirm = inventory.confirm(bookingId);   // idempotent remote command
        tx.executeWithoutResult(s -> {
            if (!inbox.firstDelivery(CONSUMER, eventId)) {
                return;
            }
            Booking b = bookings.findById(bookingId).orElseThrow();
            if (b.getStatus() != BookingStatus.PENDING_PAYMENT) {
                return;
            }
            if (!confirm.getSuccess()) {
                // seats were lost (hold expired and re-sold): fail + refund
                b.fail("SEATS_LOST");
                outbox.record("Booking", bookingId, EventTypes.REFUND_REQUESTED, Topics.PAYMENT_COMMANDS,
                        new RefundRequestedCommand(bookingId, "SEATS_LOST"));
                outbox.record("Booking", bookingId, EventTypes.BOOKING_CANCELLED, Topics.BOOKING_EVENTS,
                        new BookingCancelledEvent(bookingId, b.getUserId(), b.getUserEmail(), "SEATS_LOST"));
                return;
            }
            b.confirm(tickets.generate(b.getId(), b.getShowId(), b.getSeats()));
            outbox.record("Booking", bookingId, EventTypes.BOOKING_CONFIRMED, Topics.BOOKING_EVENTS,
                    new BookingConfirmedEvent(b.getId(), b.getUserId(), b.getUserEmail(), b.getShowId(), b.getEventTitle(),
                            b.getVenueName(), b.getShowTime(), java.util.List.copyOf(b.getSeats()), b.getTotalAmount(),
                            b.getCurrency(), b.getTicketCode()));
        });
    }

    /** Terminal payment failure: release seats and fail the booking. */
    public void onPaymentFailed(String eventId, UUID bookingId, String reason) {
        Booking current = bookings.findById(bookingId).orElse(null);
        if (current == null || !current.getStatus().isActive()) {
            return;
        }
        inventory.release(bookingId);
        tx.executeWithoutResult(s -> {
            if (!inbox.firstDelivery(CONSUMER, eventId)) {
                return;
            }
            Booking b = bookings.findById(bookingId).orElseThrow();
            if (!b.getStatus().isActive()) {
                return;
            }
            b.fail("PAYMENT_FAILED: " + reason);
            outbox.record("Booking", bookingId, EventTypes.BOOKING_CANCELLED, Topics.BOOKING_EVENTS,
                    new BookingCancelledEvent(bookingId, b.getUserId(), b.getUserEmail(), "PAYMENT_FAILED"));
        });
    }

    /**
     * Payment window elapsed. Cancel the PaymentIntent FIRST: if it already succeeded we must not release the seats
     * (the PaymentSucceeded event will confirm the booking).
     */
    public void expire(UUID bookingId) {
        Booking current = bookings.findById(bookingId).orElse(null);
        if (current == null || !current.getStatus().isActive()) {
            return;
        }
        if (current.getStatus() == BookingStatus.PENDING_PAYMENT) {
            PaymentClient.CancelPaymentResponse cancel = payments.cancelPayment(bookingId);
            if ("ALREADY_SUCCEEDED".equals(cancel.status())) {
                // Reconciliation: the money was captured but the PaymentSucceeded event was not (yet) applied
                // (lost, parked on a DLT, or still in flight). Complete the saga from here; it is idempotent.
                log.info("Booking {} was paid - reconciling instead of expiring", bookingId);
                onPaymentSucceeded("reconcile-" + bookingId, bookingId);
                return;
            }
        }
        inventory.release(bookingId);
        tx.executeWithoutResult(s -> {
            Booking b = bookings.findById(bookingId).orElseThrow();
            if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                b.expire();
            } else if (b.getStatus() == BookingStatus.PENDING_SEATS) {
                b.fail("STUCK_IN_PENDING_SEATS");
            } else {
                return;
            }
            outbox.record("Booking", bookingId, EventTypes.BOOKING_CANCELLED, Topics.BOOKING_EVENTS,
                    new BookingCancelledEvent(bookingId, b.getUserId(), b.getUserEmail(), b.getFailureReason()));
        });
    }
}
