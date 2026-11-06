package com.bmtickets.common.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Published by catalog-service when a show is scheduled; seat-inventory-service generates the seat map from it. */
public record ShowScheduledEvent(UUID showId, UUID venueId, Instant startTime, List<SeatSection> sections) {

    /** A block of seats sharing a price category, e.g. rows A..C, 20 seats each, category PREMIUM. */
    public record SeatSection(String category, List<String> rows, int seatsPerRow) {
    }
}
