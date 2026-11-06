package com.bmtickets.catalog.config;

import com.bmtickets.catalog.api.CatalogDtos.EventDetails;
import com.bmtickets.catalog.api.CatalogDtos.EventPage;
import com.bmtickets.catalog.api.CatalogDtos.ShowResponse;
import com.bmtickets.catalog.service.CacheNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Typed JSON serializers per cache (no polymorphic default typing => no deserialization gadget risk) and
 * per-cache TTLs. Short TTLs bound staleness even if an eviction is missed.
 */
@Configuration
public class CacheConfig {

    @Bean
    RedisCacheManagerBuilderCustomizer cacheCustomizer(ObjectMapper mapper) {
        return builder -> builder
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                        .prefixCacheNameWith("catalog::")
                        .disableCachingNullValues()
                        .entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration(CacheNames.SHOW, typed(mapper, ShowResponse.class, Duration.ofMinutes(10)))
                .withCacheConfiguration(CacheNames.EVENT, typed(mapper, EventDetails.class, Duration.ofMinutes(5)))
                .withCacheConfiguration(CacheNames.EVENTS_BY_CITY, typed(mapper, EventPage.class, Duration.ofMinutes(2)));
    }

    private static <T> RedisCacheConfiguration typed(ObjectMapper mapper, Class<T> type, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("catalog::")
                .disableCachingNullValues()
                .entryTtl(ttl)
                .serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, type)));
    }
}
