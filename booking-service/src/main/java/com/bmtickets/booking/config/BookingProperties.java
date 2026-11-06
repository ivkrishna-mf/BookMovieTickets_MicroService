package com.bmtickets.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param paymentWindow        time the user has to pay after seats are held
 * @param seatHoldTtl          seat hold duration (> paymentWindow so a just-in-time payment can still confirm)
 * @param attemptsPerUserShow  anti-hoarding: booking attempts per user per show (bucket size)
 * @param attemptsRefillPerHour refill rate of that bucket
 */
@ConfigurationProperties("app.booking")
public record BookingProperties(
        @DefaultValue("PT8M") Duration paymentWindow,
        @DefaultValue("PT10M") Duration seatHoldTtl,
        @DefaultValue("10") long attemptsPerUserShow,
        @DefaultValue("10") double attemptsRefillPerHour) {
}
