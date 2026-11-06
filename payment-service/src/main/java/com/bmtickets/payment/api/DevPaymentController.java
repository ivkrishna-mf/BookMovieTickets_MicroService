package com.bmtickets.payment.api;

import com.bmtickets.payment.gateway.PaymentGateway;
import com.bmtickets.payment.service.PaymentService;
import com.bmtickets.payment.service.PaymentService.PaymentView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Local development only (app.payment.provider=fake): simulates Stripe's payment_intent.succeeded webhook. */
@RestController
@RequestMapping("/api/v1/payments/dev")
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "fake")
public class DevPaymentController {

    private final PaymentService service;

    public DevPaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/bookings/{bookingId}/succeed")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void succeed(@PathVariable UUID bookingId) {
        PaymentView p = service.findByBooking(bookingId);
        service.handleProviderEvent(new PaymentGateway.ProviderEvent("dev-" + UUID.randomUUID(),
                PaymentGateway.ProviderEvent.Type.SUCCEEDED, p.providerPaymentId(), null));
    }
}
