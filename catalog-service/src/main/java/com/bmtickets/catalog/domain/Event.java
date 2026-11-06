package com.bmtickets.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A movie, concert, play or match that can have many shows. */
@Entity
@Table(name = "event")
public class Event {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String title;
    @Column(columnDefinition = "text")
    private String description;
    @Column(nullable = false)
    private String category;
    private String language;
    @Column(name = "duration_minutes")
    private int durationMinutes;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Event() {
    }

    public Event(String title, String description, String category, String language, int durationMinutes) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.description = description;
        this.category = category;
        this.language = language;
        this.durationMinutes = durationMinutes;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getLanguage() { return language; }
    public int getDurationMinutes() { return durationMinutes; }
    public Instant getCreatedAt() { return createdAt; }
}
