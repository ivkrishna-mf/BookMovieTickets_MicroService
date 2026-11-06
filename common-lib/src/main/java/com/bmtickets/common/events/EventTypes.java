package com.bmtickets.common.events;

/** Values of the {@code eventType} Kafka header. Consumers dispatch on this header. */
public final class EventTypes {
    public static final String SHOW_SCHEDULED = "ShowScheduled";
    public static final String BOOKING_CONFIRMED = "BookingConfirmed";
    public static final String BOOKING_CANCELLED = "BookingCancelled";
    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String PAYMENT_REFUNDED = "PaymentRefunded";
    public static final String REFUND_REQUESTED = "RefundRequested";

    public static final String HEADER_EVENT_TYPE = "eventType";
    public static final String HEADER_EVENT_ID = "eventId";

    private EventTypes() {
    }
}
