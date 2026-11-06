package com.bmtickets.payment.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Local/dev adapter (app.payment.provider=fake): no external calls; success is simulated via DevPaymentController. */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "fake")
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public GatewayIntent createIntent(UUID bookingId, BigDecimal amount, String currency, String customerEmail) {
        String id = "pi_fake_" + bookingId.toString().replace("-", "");
        return new GatewayIntent(id, id + "_secret_fake");
    }

    @Override
    public CancelOutcome cancel(String providerPaymentId) {
        return CancelOutcome.CANCELED;   // PaymentService checks local SUCCEEDED state before calling this
    }

    @Override
    public String refund(String providerPaymentId, UUID bookingId) {
        return "re_fake_" + bookingId.toString().replace("-", "");
    }

    @Override
    public ProviderEvent parseWebhook(String payload, String signatureHeader) {
        throw new UnsupportedOperationException("Webhooks are not used with the fake provider");
    }
}
