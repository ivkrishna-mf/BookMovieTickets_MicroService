package com.bmtickets.booking.idempotency;

import com.bmtickets.common.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Idempotency-Key handling (same semantics as Stripe's API):
 * <ul>
 *   <li>first request with a key: lock it (SET NX, short TTL) and execute;</li>
 *   <li>concurrent duplicate (double click, client retry while still running): 409 REQUEST_IN_PROGRESS;</li>
 *   <li>later duplicate: replay the stored response, nothing is executed twice;</li>
 *   <li>same key, different payload: 422 - a key identifies exactly one intent.</li>
 * </ul>
 * Redis gives the fast path; the DB unique constraint (user_id, idempotency_key) is the durable backstop if a Redis
 * record is lost (eviction / failover).
 */
@Component
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration lockTtl;
    private final Duration resultTtl;

    public IdempotencyService(StringRedisTemplate redis, ObjectMapper mapper,
                              @Value("${app.idempotency.lock-ttl:PT60S}") Duration lockTtl,
                              @Value("${app.idempotency.result-ttl:PT24H}") Duration resultTtl) {
        this.redis = redis;
        this.mapper = mapper;
        this.lockTtl = lockTtl;
        this.resultTtl = resultTtl;
    }

    public record Outcome<T>(T value, boolean replayed) {
    }

    public record StoredRecord(String state, String requestHash, String body) {
    }

    public <T> Outcome<T> execute(String scope, String key, String requestHash, Class<T> type, Supplier<T> action) {
        String redisKey = "idem:" + scope + ":" + key;
        StoredRecord inProgress = new StoredRecord("IN_PROGRESS", requestHash, null);

        Boolean acquired;
        try {
            acquired = redis.opsForValue().setIfAbsent(redisKey, write(inProgress), lockTtl);
        } catch (RuntimeException e) {
            // Redis down: proceed; the DB unique constraint still prevents duplicate bookings.
            log.warn("Idempotency store unavailable, relying on DB constraint: {}", e.getMessage());
            return new Outcome<>(action.get(), false);
        }

        if (!Boolean.TRUE.equals(acquired)) {
            StoredRecord existing = read(redis.opsForValue().get(redisKey));
            if (existing == null) {
                throw new BusinessException(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", "Please retry");
            }
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was already used with a different request");
            }
            if ("IN_PROGRESS".equals(existing.state())) {
                throw new BusinessException(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS",
                        "An identical request is still being processed", Map.of("retryAfterSeconds", 2));
            }
            try {
                return new Outcome<>(mapper.readValue(existing.body(), type), true);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Corrupt idempotency record", e);
            }
        }

        try {
            T result = action.get();
            redis.opsForValue().set(redisKey, write(new StoredRecord("COMPLETED", requestHash, write(result))), resultTtl);
            return new Outcome<>(result, false);
        } catch (RuntimeException e) {
            // failed attempts are not cached: the client may retry with the same key
            safeDelete(redisKey);
            throw e;
        }
    }

    private void safeDelete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException ignored) {
            // lock expires by TTL
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private StoredRecord read(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, StoredRecord.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
