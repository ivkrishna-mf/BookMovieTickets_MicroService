package com.bmtickets.common.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Inbox / idempotent-consumer record: (consumer, eventId) processed exactly once per consumer. */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    private String id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String consumer, String eventId) {
        this.id = consumer + ":" + eventId;
        this.processedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
