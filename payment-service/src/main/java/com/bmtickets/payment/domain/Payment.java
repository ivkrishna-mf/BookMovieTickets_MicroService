package com.bmtickets.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One payment per booking (unique booking_id) => creating a payment twice for the same booking is impossible. */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    private UUID id;
    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "customer_email")
    private String customerEmail;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    @Column(name = "provider_payment_id", unique = true)
    private String providerPaymentId;
    @Column(name = "client_secret")
    private String clientSecret;
    @Column(name = "provider_refund_id")
    private String providerRefundId;
    @Column(name = "last_error")
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Payment() {
    }

    public Payment(UUID bookingId, BigDecimal amount, String currency, String customerEmail) {
        this.id = UUID.randomUUID();
        this.bookingId = bookingId;
        this.amount = amount;
        this.currency = currency;
        this.customerEmail = customerEmail;
        this.status = PaymentStatus.CREATING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void attachIntent(String providerPaymentId, String clientSecret) {
        if (status == PaymentStatus.CREATING) {
            this.providerPaymentId = providerPaymentId;
            this.clientSecret = clientSecret;
            this.status = PaymentStatus.REQUIRES_PAYMENT;
        }
    }

    /** @return true if this call changed the state (emit an event exactly then) */
    public boolean markSucceeded() {
        if (status == PaymentStatus.SUCCEEDED || status == PaymentStatus.REFUNDED) {
            return false;
        }
        this.status = PaymentStatus.SUCCEEDED;
        this.clientSecret = null;
        return true;
    }

    public boolean markCanceled() {
        if (status == PaymentStatus.CREATING || status == PaymentStatus.REQUIRES_PAYMENT) {
            this.status = PaymentStatus.CANCELED;
            this.clientSecret = null;
            return true;
        }
        return false;
    }

    public boolean markRefunded(String refundId) {
        if (status != PaymentStatus.SUCCEEDED) {
            return false;
        }
        this.status = PaymentStatus.REFUNDED;
        this.providerRefundId = refundId;
        return true;
    }

    public void recordError(String error) {
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getCustomerEmail() { return customerEmail; }
    public PaymentStatus getStatus() { return status; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getClientSecret() { return clientSecret; }
    public String getLastError() { return lastError; }
}
