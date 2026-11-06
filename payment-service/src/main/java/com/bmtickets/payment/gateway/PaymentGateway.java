package com.bmtickets.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Anti-corruption layer / port: the domain speaks this neutral language; Stripe (or Razorpay, Adyen...) specifics
 * live only in an adapter. Swapping or adding a PSP never touches the payment domain or the booking saga.
 */
public interface PaymentGateway {

    GatewayIntent createIntent(UUID bookingId, BigDecimal amount, String currency, String customerEmail);

    CancelOutcome cancel(String providerPaymentId);

    String refund(String providerPaymentId, UUID bookingId);

    /** Verifies the webhook signature and maps it to a neutral event; null for event types we don't care about. */
    ProviderEvent parseWebhook(String payload, String signatureHeader);

    record GatewayIntent(String providerPaymentId, String clientSecret) {
    }

    enum CancelOutcome { CANCELED, ALREADY_SUCCEEDED }

    record ProviderEvent(String id, Type type, String providerPaymentId, String message) {
        public enum Type { SUCCEEDED, FAILED_ATTEMPT, CANCELED, REFUNDED }
    }
}
