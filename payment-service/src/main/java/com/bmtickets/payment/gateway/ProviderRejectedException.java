package com.bmtickets.payment.gateway;

import com.bmtickets.common.error.BusinessException;
import org.springframework.http.HttpStatus;

/** Permanent PSP rejection (invalid request, card error). Not retried, not a circuit-breaker failure. */
public class ProviderRejectedException extends BusinessException {
    public ProviderRejectedException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_PROVIDER_REJECTED", message);
    }
}
