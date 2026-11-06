package com.bmtickets.payment.api;

import com.bmtickets.payment.service.PaymentService;
import com.bmtickets.payment.service.PaymentService.PaymentView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentQueryController {

    private final PaymentService service;

    public PaymentQueryController(PaymentService service) {
        this.service = service;
    }

    @GetMapping("/bookings/{bookingId}")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentView byBooking(@PathVariable UUID bookingId) {
        return service.findByBooking(bookingId);
    }
}
