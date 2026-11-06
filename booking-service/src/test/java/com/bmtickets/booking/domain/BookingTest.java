package com.bmtickets.booking.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    private Booking newBooking() {
        return Booking.initiate("user-1", "u@example.com", UUID.randomUUID(), "Movie", "Venue",
                Instant.now().plusSeconds(86_400), List.of("A-1", "A-2"), "INR", "key-1234567890abcdef");
    }

    @Test
    void happyPath() {
        Booking b = newBooking();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING_SEATS);
        b.awaitPayment(new BigDecimal("500.00"), UUID.randomUUID(), "secret", Instant.now().plusSeconds(480));
        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        b.confirm("ticket");
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b.getPaymentClientSecret()).isNull();
    }

    @Test
    void cannotConfirmWithoutPendingPayment() {
        assertThatThrownBy(() -> newBooking().confirm("t")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failIsIgnoredOnceBookingIsTerminal() {
        Booking b = newBooking();
        b.awaitPayment(BigDecimal.TEN, UUID.randomUUID(), "s", Instant.now());
        b.confirm("t");
        b.fail("late failure");
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }
}
