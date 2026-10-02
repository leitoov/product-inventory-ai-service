package com.inventory.infrastructure.config;

/**
 * Configuration for a single agent level.
 * Bound from the 'ai.agents.levels[*]' list in application.yml.
 */
public record AgentLevelConfig(
        String name,
        String endpoint,
        String apiKey,
        String model,
        long timeoutMs,
        double minConfidence,
        int maxRetries
) {}
