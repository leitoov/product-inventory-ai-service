package com.inventory.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers all @ConfigurationProperties beans used across the application.
 */
@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        AiProviderProperties.class
})
public class AppConfig {
}
