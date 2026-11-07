package com.bmtickets.catalog.api;

import com.bmtickets.common.events.ShowScheduledEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** API contracts (records = immutable, cache-serializable). */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CreateVenueRequest(@NotBlank String name, @NotBlank String city, String address, @Min(1) int capacity) {
    }

    public record CreateEventRequest(@NotBlank String title, String description, @NotBlank String category,
                                     String language, @Min(1) int durationMinutes) {
    }

    public record CreateShowRequest(
            @NotNull UUID eventId,
            @NotNull UUID venueId,
            @NotNull @Future Instant startTime,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @Min(1) @Max(20) int maxSeatsPerBooking,
            @NotEmpty Map<String, BigDecimal> priceByCategory,
            @NotEmpty List<ShowScheduledEvent.@Valid SeatSection> sections) {
    }

    public record IdResponse(UUID id) {
    }

    public record EventSummary(UUID id, String title, String category, String language, int durationMinutes) {
    }

    public record EventPage(List<EventSummary> items, int page, int size, long totalElements, int totalPages) {
    }

    public record ShowSummary(UUID id, UUID venueId, String venueName, String city, Instant startTime, String status) {
    }

    public record EventDetails(UUID id, String title, String description, String category, String language,
                               int durationMinutes, List<ShowSummary> upcomingShows) {
    }

    public record ShowResponse(UUID id, UUID eventId, String eventTitle, UUID venueId, String venueName, String city,
                               Instant startTime, String status, String currency, int maxSeatsPerBooking,
                               Map<String, BigDecimal> priceByCategory) {
    }
}
