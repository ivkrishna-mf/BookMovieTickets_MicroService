package com.bmtickets.common.events;

import java.util.UUID;

public record PaymentRefundedEvent(UUID paymentId, UUID bookingId, String providerRefundId) {
}
