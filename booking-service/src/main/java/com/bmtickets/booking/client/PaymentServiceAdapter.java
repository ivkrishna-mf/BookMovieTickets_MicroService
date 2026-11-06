package com.bmtickets.booking.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Resilience wrapper around the Feign client. payment-service is idempotent on bookingId, so retry is safe. */
@Component
public class PaymentServiceAdapter {

    private final PaymentClient client;

    public PaymentServiceAdapter(PaymentClient client) {
        this.client = client;
    }

    @Retry(name = "payment")
    @CircuitBreaker(name = "payment")
    public PaymentClient.CreatePaymentResponse createPayment(UUID bookingId, BigDecimal amount, String currency, String email) {
        return client.create(new PaymentClient.CreatePaymentRequest(bookingId, amount, currency, email));
    }

    @Retry(name = "payment")
    @CircuitBreaker(name = "payment")
    public PaymentClient.CancelPaymentResponse cancelPayment(UUID bookingId) {
        return client.cancel(bookingId);
    }
}
