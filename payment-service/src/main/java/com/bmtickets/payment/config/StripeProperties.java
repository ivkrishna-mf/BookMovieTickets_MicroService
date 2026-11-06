package com.bmtickets.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param apiKey         secret key (from AWS Secrets Manager via External Secrets, never in the image)
 * @param webhookSecret  endpoint signing secret (whsec_...)
 * @param ratePerSecond  cluster-wide budget for Stripe API calls (Stripe live limit is ~100 req/s)
 * @param burst          bucket size for short bursts
 */
@ConfigurationProperties("app.stripe")
public record StripeProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("") String webhookSecret,
        @DefaultValue("80") double ratePerSecond,
        @DefaultValue("100") long burst) {
}
