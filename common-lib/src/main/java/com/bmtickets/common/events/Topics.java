package com.bmtickets.common.events;

/** Kafka topic names. One topic per aggregate stream; key = aggregate id to keep per-aggregate ordering. */
public final class Topics {
    public static final String SHOW_EVENTS = "catalog.show-events";
    public static final String BOOKING_EVENTS = "booking.events";
    public static final String PAYMENT_EVENTS = "payment.events";
    public static final String PAYMENT_COMMANDS = "payment.commands";

    private Topics() {
    }
}
