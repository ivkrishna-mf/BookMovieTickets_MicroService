package com.bmtickets.booking.domain;

/**
 * Saga state machine:
 * <pre>
 * PENDING_SEATS --hold ok--> PENDING_PAYMENT --payment ok + seats confirmed--> CONFIRMED
 *       |                          |  \--payment failed / user cancel--> CANCELLED / FAILED
 *       \--hold failed--> FAILED   \--payment window elapsed--> EXPIRED
 * </pre>
 */
public enum BookingStatus {
    PENDING_SEATS, PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED, FAILED;

    public boolean isActive() {
        return this == PENDING_SEATS || this == PENDING_PAYMENT;
    }
}
