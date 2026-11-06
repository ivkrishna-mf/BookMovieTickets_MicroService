package com.bmtickets.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Audit + de-duplication: (booking_id, type) is unique, so a ticket e-mail is sent once per booking. */
@Entity
@Table(name = "notification_log")
public class NotificationLog {

    @Id
    private UUID id;
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;
    @Column(nullable = false)
    private String type;
    private String recipient;
    @Column(nullable = false)
    private String status;
    @Column(name = "provider_message_id")
    private String providerMessageId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationLog() {
    }

    public NotificationLog(UUID bookingId, String type, String recipient) {
        this.id = UUID.randomUUID();
        this.bookingId = bookingId;
        this.type = type;
        this.recipient = recipient;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public void markSent(String providerMessageId) {
        this.status = "SENT";
        this.providerMessageId = providerMessageId;
        this.sentAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getStatus() { return status; }
}
