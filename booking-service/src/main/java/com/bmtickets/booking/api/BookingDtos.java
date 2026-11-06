package com.bmtickets.booking.api;

import com.bmtickets.booking.domain.Booking;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record CreateBookingRequest(
            @NotNull UUID showId,
            @NotEmpty @Size(max = 20) List<@Pattern(regexp = "[A-Z]{1,3}-[0-9]{1,4}") String> seats) {
    }

    public record BookingResponse(UUID id, String status, UUID showId, String eventTitle, String venueName,
                                  Instant showTime, List<String> seats, BigDecimal totalAmount, String currency,
                                  String paymentClientSecret, Instant expiresAt, String ticketCode,
                                  String failureReason, Instant createdAt) {

        public static BookingResponse from(Booking b) {
            return new BookingResponse(b.getId(), b.getStatus().name(), b.getShowId(), b.getEventTitle(),
                    b.getVenueName(), b.getShowTime(), List.copyOf(b.getSeats()), b.getTotalAmount(), b.getCurrency(),
                    b.getPaymentClientSecret(), b.getExpiresAt(), b.getTicketCode(), b.getFailureReason(), b.getCreatedAt());
        }
    }
}
