package com.bmtickets.booking.saga;

import com.bmtickets.booking.api.BookingDtos.BookingResponse;
import com.bmtickets.booking.api.BookingDtos.CreateBookingRequest;
import com.bmtickets.booking.client.CatalogClient;
import com.bmtickets.booking.client.InventoryClient;
import com.bmtickets.booking.client.PaymentClient;
import com.bmtickets.booking.client.PaymentServiceAdapter;
import com.bmtickets.booking.client.ShowInfo;
import com.bmtickets.booking.config.BookingProperties;
import com.bmtickets.booking.domain.Booking;
import com.bmtickets.booking.domain.BookingRepository;
import com.bmtickets.common.error.BusinessException;
import com.bmtickets.common.error.TooManyRequestsException;
import com.bmtickets.common.events.BookingCancelledEvent;
import com.bmtickets.common.events.EventTypes;
import com.bmtickets.common.events.Topics;
import com.bmtickets.common.messaging.OutboxService;
import com.bmtickets.common.ratelimit.BucketSpec;
import com.bmtickets.common.ratelimit.DistributedRateLimiter;
import com.bmtickets.common.security.CurrentUser;
import com.bmtickets.grpc.inventory.v1.HeldSeat;
import com.bmtickets.grpc.inventory.v1.HoldSeatsResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestration-based saga for the booking flow (synchronous part).
 * <pre>
 *  1. Catalog.GetShow        (gRPC)  validate show, prices
 *  2. persist PENDING_SEATS  (local tx; unique constraints stop duplicates)
 *  3. Inventory.HoldSeats    (gRPC)  all-or-nothing, idempotent on bookingId
 *  4. Payment.create         (Feign) PaymentIntent, idempotent on bookingId
 *  5. persist PENDING_PAYMENT (local tx) -> client confirms payment with Stripe.js using clientSecret
 *  -- asynchronous continuation in {@link PaymentEventsListener} --
 * </pre>
 * Any failure after step 3 runs compensations (release seats, mark FAILED). No distributed (2PC/XA) transaction:
 * each step is a local transaction + an idempotent remote command, which scales and tolerates partial failure.
 */
@Service
public class BookingSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BookingSagaOrchestrator.class);

    private final BookingRepository bookings;
    private final CatalogClient catalog;
    private final InventoryClient inventory;
    private final PaymentServiceAdapter payments;
    private final OutboxService outbox;
    private final DistributedRateLimiter rateLimiter;
    private final TransactionTemplate tx;
    private final BookingProperties props;

    public BookingSagaOrchestrator(BookingRepository bookings, CatalogClient catalog, InventoryClient inventory,
                                   PaymentServiceAdapter payments, OutboxService outbox,
                                   DistributedRateLimiter rateLimiter, TransactionTemplate tx, BookingProperties props) {
        this.bookings = bookings;
        this.catalog = catalog;
        this.inventory = inventory;
        this.payments = payments;
        this.outbox = outbox;
        this.rateLimiter = rateLimiter;
        this.tx = tx;
        this.props = props;
    }

    /**
     * Bulkhead = max concurrent sagas per pod (throttling by concurrency); RateLimiter = max saga starts per second
     * per pod. Both protect downstream services from a single pod's overload; the gateway enforces per-user limits.
     */
    @Bulkhead(name = "createBooking")
    @RateLimiter(name = "createBooking")
    @Observed(name = "booking.saga.create", contextualName = "create-booking-saga")
    public BookingResponse createBooking(CurrentUser user, CreateBookingRequest request, String idempotencyKey) {
        List<String> seats = request.seats().stream().distinct().sorted().toList();

        // Business rate limit (anti seat-hoarding / scalper bots): N attempts per user per show, cluster-wide.
        var decision = rateLimiter.tryAcquire("booking-attempts:" + user.id() + ":" + request.showId(),
                new BucketSpec(props.attemptsPerUserShow(), props.attemptsRefillPerHour() / 3600.0));
        if (!decision.allowed()) {
            throw new TooManyRequestsException("Too many booking attempts for this show",
                    (long) Math.ceil(decision.retryAfterMillis() / 1000.0));
        }

        // ---- step 1: show lookup (gRPC) ----
        ShowInfo show = catalog.getShow(request.showId());
        validate(show, seats);

        // ---- step 2: create booking record ----
        Booking booking;
        try {
            booking = tx.execute(s -> bookings.saveAndFlush(Booking.initiate(user.id(), user.email(), show.showId(),
                    show.eventTitle(), show.venueName(), show.startTime(), seats, show.currency(), idempotencyKey)));
        } catch (DataIntegrityViolationException e) {
            return bookings.findByUserIdAndIdempotencyKey(user.id(), idempotencyKey)
                    .map(BookingResponse::from)                      // same key replayed => same booking
                    .orElseThrow(() -> BusinessException.conflict("ACTIVE_BOOKING_EXISTS",
                            "You already have a booking in progress for this show"));
        }
        UUID bookingId = booking.getId();

        try {
            // ---- step 3: hold seats (gRPC) ----
            HoldSeatsResponse hold = inventory.hold(show.showId(), seats, bookingId, props.seatHoldTtl());
            if (!hold.getSuccess()) {
                markFailed(bookingId, hold.getFailureReason());
                if ("THROTTLED".equals(hold.getFailureReason())) {
                    throw new TooManyRequestsException("Very high demand for this show, please retry in a moment", 2);
                }
                throw new BusinessException(HttpStatus.CONFLICT, "SEATS_UNAVAILABLE", "Some seats are no longer available",
                        Map.of("unavailableSeats", hold.getUnavailableSeatsList()));
            }
            BigDecimal total = price(show, hold.getSeatsList());

            // ---- step 4: payment intent (Feign -> payment-service -> Stripe) ----
            PaymentClient.CreatePaymentResponse payment =
                    payments.createPayment(bookingId, total, show.currency(), user.email());

            // ---- step 5: await payment ----
            Booking updated = tx.execute(s -> {
                Booking b = bookings.findById(bookingId).orElseThrow();
                b.awaitPayment(total, payment.paymentId(), payment.clientSecret(), Instant.now().plus(props.paymentWindow()));
                return b;
            });
            return BookingResponse.from(updated);

        } catch (BusinessException e) {
            throw e;   // business outcome already handled (booking marked FAILED)
        } catch (RuntimeException e) {
            // ---- compensation ----
            log.warn("Booking saga {} failed, compensating: {}", bookingId, e.toString());
            compensateRelease(bookingId);
            markFailed(bookingId, "TECHNICAL_FAILURE");
            throw e;   // mapped to 503 (circuit open) / 500 by the global handler
        }
    }

    /** User-initiated cancellation of an unpaid booking. */
    public BookingResponse cancelPending(UUID bookingId, String userId) {
        Booking booking = bookings.findById(bookingId).orElseThrow(() -> BusinessException.notFound("Booking", bookingId));
        if (!booking.getUserId().equals(userId)) {
            throw BusinessException.notFound("Booking", bookingId);   // don't reveal others' bookings
        }
        if (!booking.getStatus().isActive()) {
            throw BusinessException.conflict("NOT_CANCELLABLE", "Only unpaid bookings can be cancelled here");
        }
        PaymentClient.CancelPaymentResponse cancel = payments.cancelPayment(bookingId);
        if ("ALREADY_SUCCEEDED".equals(cancel.status())) {
            throw BusinessException.conflict("ALREADY_PAID", "Payment already completed; the booking is being confirmed");
        }
        compensateRelease(bookingId);
        Booking cancelled = tx.execute(s -> {
            Booking b = bookings.findById(bookingId).orElseThrow();
            b.cancel("USER_CANCELLED");
            outbox.record("Booking", b.getId(), EventTypes.BOOKING_CANCELLED, Topics.BOOKING_EVENTS,
                    new BookingCancelledEvent(b.getId(), b.getUserId(), b.getUserEmail(), "USER_CANCELLED"));
            return b;
        });
        return BookingResponse.from(cancelled);
    }

    private void validate(ShowInfo show, List<String> seats) {
        if (!"OPEN".equals(show.status()) || show.startTime().isBefore(Instant.now())) {
            throw BusinessException.conflict("SHOW_NOT_BOOKABLE", "Show is not open for booking");
        }
        if (seats.size() > show.maxSeatsPerBooking()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TOO_MANY_SEATS",
                    "At most " + show.maxSeatsPerBooking() + " seats per booking");
        }
    }

    private static BigDecimal price(ShowInfo show, List<HeldSeat> held) {
        return held.stream()
                .map(s -> {
                    BigDecimal p = show.priceByCategory().get(s.getCategory());
                    if (p == null) {
                        throw new IllegalStateException("No price for category " + s.getCategory());
                    }
                    return p;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    void compensateRelease(UUID bookingId) {
        try {
            inventory.release(bookingId);
        } catch (RuntimeException e) {
            // the inventory sweeper releases expired holds anyway (eventual consistency safety net)
            log.error("Seat release failed for booking {} - will expire via sweeper: {}", bookingId, e.toString());
        }
    }

    void markFailed(UUID bookingId, String reason) {
        tx.executeWithoutResult(s -> bookings.findById(bookingId).ifPresent(b -> b.fail(reason)));
    }
}
