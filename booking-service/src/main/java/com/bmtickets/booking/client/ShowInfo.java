package com.bmtickets.booking.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ShowInfo(UUID showId, String eventTitle, String venueName, Instant startTime, String status,
                       String currency, int maxSeatsPerBooking, Map<String, BigDecimal> priceByCategory) {
}
