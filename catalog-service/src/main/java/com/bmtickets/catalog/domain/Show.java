package com.bmtickets.catalog.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One performance/screening of an event at a venue, with a price per seat category. */
@Entity
@Table(name = "shows")
public class Show {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShowStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "max_seats_per_booking", nullable = false)
    private int maxSeatsPerBooking;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "show_price", joinColumns = @JoinColumn(name = "show_id"))
    @MapKeyColumn(name = "category")
    @Column(name = "price", nullable = false)
    private Map<String, BigDecimal> priceByCategory = new HashMap<>();

    @Version
    private long version;

    protected Show() {
    }

    public Show(Event event, Venue venue, Instant startTime, String currency, int maxSeatsPerBooking,
                Map<String, BigDecimal> prices) {
        this.id = UUID.randomUUID();
        this.event = event;
        this.venue = venue;
        this.startTime = startTime;
        this.currency = currency;
        this.maxSeatsPerBooking = maxSeatsPerBooking;
        this.priceByCategory.putAll(prices);
        this.status = ShowStatus.OPEN;
    }

    public void cancel() {
        this.status = ShowStatus.CANCELLED;
    }

    public UUID getId() { return id; }
    public Event getEvent() { return event; }
    public Venue getVenue() { return venue; }
    public Instant getStartTime() { return startTime; }
    public ShowStatus getStatus() { return status; }
    public String getCurrency() { return currency; }
    public int getMaxSeatsPerBooking() { return maxSeatsPerBooking; }
    public Map<String, BigDecimal> getPriceByCategory() { return priceByCategory; }
}
