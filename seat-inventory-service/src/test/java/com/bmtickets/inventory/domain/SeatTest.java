package com.bmtickets.inventory.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SeatTest {

    private final UUID show = UUID.randomUUID();

    @Test
    void availableSeatIsHoldable() {
        assertThat(new Seat(show, "A", 1, "GOLD").isHoldableBy(UUID.randomUUID(), Instant.now())).isTrue();
    }

    @Test
    void seatHeldByAnotherBookingIsNotHoldableUntilHoldExpires() {
        Seat seat = new Seat(show, "A", 1, "GOLD");
        Instant now = Instant.now();
        seat.hold(UUID.randomUUID(), now.plusSeconds(600));
        assertThat(seat.isHoldableBy(UUID.randomUUID(), now)).isFalse();
        assertThat(seat.isHoldableBy(UUID.randomUUID(), now.plusSeconds(601))).isTrue();
    }

    @Test
    void holdIsIdempotentForTheSameBooking() {
        Seat seat = new Seat(show, "A", 1, "GOLD");
        UUID ref = UUID.randomUUID();
        seat.hold(ref, Instant.now().plusSeconds(600));
        assertThat(seat.isHoldableBy(ref, Instant.now())).isTrue();
    }

    @Test
    void bookedSeatIsNeverHoldable() {
        Seat seat = new Seat(show, "A", 1, "GOLD");
        UUID ref = UUID.randomUUID();
        seat.hold(ref, Instant.now().plusSeconds(600));
        seat.book();
        assertThat(seat.isHoldableBy(ref, Instant.now())).isFalse();
        assertThat(seat.isHoldableBy(UUID.randomUUID(), Instant.now().plusSeconds(10_000))).isFalse();
    }
}
