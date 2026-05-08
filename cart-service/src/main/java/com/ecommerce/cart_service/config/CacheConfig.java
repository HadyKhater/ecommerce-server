package com.ecommerce.cart_service.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache configuration using Caffeine.
 * Provides in-memory caching for cart data to reduce database load.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Caffeine cache specification:
     * - maximumSize: 1000 entries per cache
     * - expireAfterWrite: 5 minutes
     * - recordStats: enable statistics for monitoring
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("carts");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .removalListener((key, value, cause) -> 
                    log.debug("Cache entry removed - key: {}, cause: {}", key, cause)));
        return cacheManager;
    }
}
