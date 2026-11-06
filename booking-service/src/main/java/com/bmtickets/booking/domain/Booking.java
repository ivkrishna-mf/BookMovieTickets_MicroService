package com.bmtickets.booking.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Booking aggregate. State transitions are guarded here; @Version makes concurrent transitions (expiry vs payment) safe. */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private String userId;
    @Column(name = "user_email")
    private String userEmail;
    @Column(name = "show_id", nullable = false)
    private UUID showId;
    @Column(name = "event_title")
    private String eventTitle;
    @Column(name = "venue_name")
    private String venueName;
    @Column(name = "show_time")
    private Instant showTime;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_seat", joinColumns = @JoinColumn(name = "booking_id"))
    @OrderColumn(name = "position")
    @Column(name = "seat_number", nullable = false)
    private List<String> seats = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;
    @Column(name = "total_amount")
    private BigDecimal totalAmount;
    @Column(length = 3)
    private String currency;
    @Column(name = "payment_id")
    private UUID paymentId;
    @Column(name = "payment_client_secret")
    private String paymentClientSecret;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Column(name = "failure_reason")
    private String failureReason;
    @Column(name = "ticket_code")
    private String ticketCode;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Booking() {
    }

    public static Booking initiate(String userId, String userEmail, UUID showId, String eventTitle, String venueName,
                                   Instant showTime, List<String> seats, String currency, String idempotencyKey) {
        Booking b = new Booking();
        b.id = UUID.randomUUID();
        b.userId = userId;
        b.userEmail = userEmail;
        b.showId = showId;
        b.eventTitle = eventTitle;
        b.venueName = venueName;
        b.showTime = showTime;
        b.seats.addAll(seats);
        b.currency = currency;
        b.idempotencyKey = idempotencyKey;
        b.status = BookingStatus.PENDING_SEATS;
        b.createdAt = Instant.now();
        b.updatedAt = b.createdAt;
        return b;
    }

    public void awaitPayment(BigDecimal total, UUID paymentId, String clientSecret, Instant expiresAt) {
        require(BookingStatus.PENDING_SEATS);
        this.totalAmount = total;
        this.paymentId = paymentId;
        this.paymentClientSecret = clientSecret;
        this.expiresAt = expiresAt;
        this.status = BookingStatus.PENDING_PAYMENT;
    }

    public void confirm(String ticketCode) {
        require(BookingStatus.PENDING_PAYMENT);
        this.ticketCode = ticketCode;
        this.paymentClientSecret = null;
        this.status = BookingStatus.CONFIRMED;
    }

    public void fail(String reason) {
        if (status.isActive()) {
            this.failureReason = reason;
            this.paymentClientSecret = null;
            this.status = BookingStatus.FAILED;
        }
    }

    public void expire() {
        require(BookingStatus.PENDING_PAYMENT);
        this.paymentClientSecret = null;
        this.failureReason = "PAYMENT_WINDOW_ELAPSED";
        this.status = BookingStatus.EXPIRED;
    }

    public void cancel(String reason) {
        if (!status.isActive()) {
            throw new IllegalStateException("Booking " + id + " cannot be cancelled in state " + status);
        }
        this.paymentClientSecret = null;
        this.failureReason = reason;
        this.status = BookingStatus.CANCELLED;
    }

    private void require(BookingStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Booking " + id + " expected " + expected + " but was " + status);
        }
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public String getUserEmail() { return userEmail; }
    public UUID getShowId() { return showId; }
    public String getEventTitle() { return eventTitle; }
    public String getVenueName() { return venueName; }
    public Instant getShowTime() { return showTime; }
    public List<String> getSeats() { return seats; }
    public BookingStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public UUID getPaymentId() { return paymentId; }
    public String getPaymentClientSecret() { return paymentClientSecret; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getFailureReason() { return failureReason; }
    public String getTicketCode() { return ticketCode; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
