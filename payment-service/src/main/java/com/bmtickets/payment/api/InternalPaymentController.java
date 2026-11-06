package com.bmtickets.payment.api;

import com.bmtickets.payment.service.PaymentService;
import com.bmtickets.payment.service.PaymentService.PaymentView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/** East-west API for booking-service (Feign). Requires the internal client-credentials scope (see SecurityConfig). */
@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {

    private final PaymentService service;

    public InternalPaymentController(PaymentService service) {
        this.service = service;
    }

    public record CreatePaymentRequest(@NotNull UUID bookingId, @NotNull @DecimalMin("0.01") BigDecimal amount,
                                       @NotBlank String currency, String customerEmail) {
    }

    public record CancelPaymentResponse(UUID bookingId, String status) {
    }

    @PostMapping
    public PaymentView create(@Valid @RequestBody CreatePaymentRequest r) {
        return service.create(r.bookingId(), r.amount(), r.currency(), r.customerEmail());
    }

    @PostMapping("/{bookingId}/cancel")
    public CancelPaymentResponse cancel(@PathVariable UUID bookingId) {
        return new CancelPaymentResponse(bookingId, service.cancel(bookingId));
    }
}
