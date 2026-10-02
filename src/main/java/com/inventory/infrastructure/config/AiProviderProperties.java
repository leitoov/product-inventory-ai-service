package com.inventory.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI provider configuration properties.
 * Bound to the 'ai.provider' prefix in application.yml.
 *
 * The provider name is intentionally absent — only the endpoint, key and model
 * are configured here. Swap providers by changing environment variables only.
 */
@ConfigurationProperties(prefix = "ai.provider")
public record AiProviderProperties(
        String endpoint,
        String apiKey,
        String model
) {}
