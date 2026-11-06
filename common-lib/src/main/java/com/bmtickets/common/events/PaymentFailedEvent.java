package com.bmtickets.common.events;

import java.util.UUID;

/** Terminal payment failure (intent cancelled / unrecoverable). Individual declined attempts are not terminal. */
public record PaymentFailedEvent(UUID paymentId, UUID bookingId, String reason) {
}
