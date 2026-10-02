package com.inventory.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Registers all @ConfigurationProperties beans and enables cross-cutting infrastructure features.
 */
@Configuration
@EnableCaching
@EnableRetry
@EnableConfigurationProperties({
        JwtProperties.class,
        AiProviderProperties.class,
        AgentsProperties.class,
        RagProperties.class
})
public class AppConfig {
}
