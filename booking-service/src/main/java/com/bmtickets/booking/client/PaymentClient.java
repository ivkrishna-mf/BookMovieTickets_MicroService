package com.bmtickets.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.UUID;

/** Declarative REST client (OpenFeign) resolved through service discovery + client-side load balancing. */
@FeignClient(name = "payment-service", path = "/internal/payments")
public interface PaymentClient {

    @PostMapping
    CreatePaymentResponse create(@RequestBody CreatePaymentRequest request);

    @PostMapping("/{bookingId}/cancel")
    CancelPaymentResponse cancel(@PathVariable("bookingId") UUID bookingId);

    record CreatePaymentRequest(UUID bookingId, BigDecimal amount, String currency, String customerEmail) {
    }

    record CreatePaymentResponse(UUID paymentId, String providerPaymentId, String clientSecret, String status) {
    }

    /** status: CANCELED | ALREADY_SUCCEEDED | NOT_FOUND */
    record CancelPaymentResponse(UUID bookingId, String status) {
    }
}
