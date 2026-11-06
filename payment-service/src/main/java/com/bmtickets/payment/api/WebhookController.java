package com.bmtickets.payment.api;

import com.bmtickets.payment.gateway.PaymentGateway;
import com.bmtickets.payment.service.PaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe webhook endpoint (public, unauthenticated by design). Authenticity is proven by the HMAC signature in the
 * Stripe-Signature header; replays are rejected by the timestamp tolerance and by event-id de-duplication.
 */
@RestController
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stripe", matchIfMissing = true)
public class WebhookController {

    private final PaymentGateway gateway;
    private final PaymentService service;

    public WebhookController(PaymentGateway gateway, PaymentService service) {
        this.gateway = gateway;
        this.service = service;
    }

    @PostMapping("/api/v1/payments/webhooks/stripe")
    public ResponseEntity<Void> stripe(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
        PaymentGateway.ProviderEvent event = gateway.parseWebhook(payload, signature);
        if (event != null) {
            service.handleProviderEvent(event);
        }
        return ResponseEntity.ok().build();   // 2xx quickly; Stripe retries non-2xx with back-off for up to 3 days
    }
}
