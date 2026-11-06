package com.bmtickets.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param maxHoldTtl         upper bound for a seat hold, whatever the caller asks for
 * @param showHoldBurst      per-show admission throttle: burst of hold attempts allowed
 * @param showHoldRatePerSec per-show admission throttle: sustained hold attempts/second across ALL pods
 * @param seatMapCacheTtl    micro-cache for the seat map of hot shows
 */
@ConfigurationProperties("app.inventory")
public record InventoryProperties(
        @DefaultValue("PT15M") Duration maxHoldTtl,
        @DefaultValue("300") long showHoldBurst,
        @DefaultValue("150") double showHoldRatePerSec,
        @DefaultValue("PT2S") Duration seatMapCacheTtl) {
}
