package com.bmtickets.common.events;

import java.util.UUID;

/** Published by booking-service when a booking is cancelled, expired or failed. */
public record BookingCancelledEvent(UUID bookingId, String userId, String userEmail, String reason) {
}
