package com.bmtickets.booking.ticket;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketCodeGeneratorTest {

    private final TicketCodeGenerator generator = new TicketCodeGenerator("0123456789abcdef0123456789abcdef");

    @Test
    void deterministicAndBoundToBookingFacts() {
        UUID booking = UUID.randomUUID();
        UUID show = UUID.randomUUID();
        String a = generator.generate(booking, show, List.of("A-1"));
        assertThat(a).startsWith(booking + ".").isEqualTo(generator.generate(booking, show, List.of("A-1")));
        assertThat(a).isNotEqualTo(generator.generate(booking, show, List.of("A-2")));
    }
}
