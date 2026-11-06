package com.bmtickets.payment.gateway;

/** Transient PSP failure (network, 5xx, 429, local throttle). Retryable; counted by the circuit breaker. */
public class ProviderUnavailableException extends RuntimeException {
    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
