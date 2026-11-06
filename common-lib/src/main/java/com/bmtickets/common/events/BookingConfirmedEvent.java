package com.bmtickets.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Published by booking-service after payment succeeded and seats were confirmed. Drives ticket e-mail. */
public record BookingConfirmedEvent(
        UUID bookingId,
        String userId,
        String userEmail,
        UUID showId,
        String eventTitle,
        String venueName,
        Instant showTime,
        List<String> seats,
        BigDecimal totalAmount,
        String currency,
        String ticketCode) {
}
