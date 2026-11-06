package com.bmtickets.inventory.hold;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record HoldResult(boolean success, Map<String, String> categoryBySeat, List<String> unavailableSeats,
                         String failureReason, Instant expiresAt) {

    public static HoldResult ok(Map<String, String> categoryBySeat, Instant expiresAt) {
        return new HoldResult(true, categoryBySeat, List.of(), null, expiresAt);
    }

    public static HoldResult failed(String reason, List<String> unavailable) {
        return new HoldResult(false, Map.of(), unavailable, reason, null);
    }
}
