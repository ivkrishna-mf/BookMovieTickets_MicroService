package com.bmtickets.inventory.api;

import com.bmtickets.inventory.config.InventoryProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Micro-cache (2s) for seat maps: a hot show can be viewed by 100k users at once; caching for 2 seconds collapses
 * that into ~1 DB query per 2s while staying fresh enough (actual seat selection is always validated on hold).
 */
@Component
public class SeatMapCache {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final InventoryProperties props;

    public SeatMapCache(StringRedisTemplate redis, ObjectMapper mapper, InventoryProperties props) {
        this.redis = redis;
        this.mapper = mapper;
        this.props = props;
    }

    public Optional<SeatMapResponse> get(UUID showId) {
        try {
            String json = redis.opsForValue().get(key(showId));
            return json == null ? Optional.empty() : Optional.of(mapper.readValue(json, SeatMapResponse.class));
        } catch (RuntimeException | JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public void put(UUID showId, SeatMapResponse map) {
        try {
            redis.opsForValue().set(key(showId), mapper.writeValueAsString(map), props.seatMapCacheTtl());
        } catch (RuntimeException | JsonProcessingException ignored) {
            // cache is best effort
        }
    }

    public void evict(UUID showId) {
        try {
            redis.delete(key(showId));
        } catch (RuntimeException ignored) {
            // expires anyway
        }
    }

    private static String key(UUID showId) {
        return "seatmap:" + showId;
    }
}
