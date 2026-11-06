package com.bmtickets.common.ratelimit;

public record RateLimitDecision(boolean allowed, long remaining, long retryAfterMillis) {
}
