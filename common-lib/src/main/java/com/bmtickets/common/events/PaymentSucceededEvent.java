package com.bmtickets.common.events;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentSucceededEvent(UUID paymentId, UUID bookingId, String providerPaymentId,
                                    BigDecimal amount, String currency) {
}
