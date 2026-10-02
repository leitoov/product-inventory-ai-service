package com.inventory.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache configuration for agent sessions.
 *
 * Sessions are stored in-memory with TTL-based expiry.
 * For production with multiple instances, replace with a distributed cache (Redis).
 */
@Configuration
public class CacheConfig {

    @Value("${agent.session.ttl-minutes:30}")
    private int sessionTtlMinutes;

    @Value("${agent.session.max-sessions:1000}")
    private int maxSessions;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("agentSessions");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(maxSessions)
                .expireAfterAccess(sessionTtlMinutes, TimeUnit.MINUTES)
                .recordStats());
        return manager;
    }
}
