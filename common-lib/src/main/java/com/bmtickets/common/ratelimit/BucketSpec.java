package com.bmtickets.common.ratelimit;

/**
 * @param capacity        maximum burst size
 * @param refillPerSecond sustained rate (tokens per second)
 */
public record BucketSpec(long capacity, double refillPerSecond) {
    public BucketSpec {
        if (capacity <= 0 || refillPerSecond <= 0) {
            throw new IllegalArgumentException("capacity and refillPerSecond must be > 0");
        }
    }
}
