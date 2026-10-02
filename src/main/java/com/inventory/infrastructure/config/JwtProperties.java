package com.inventory.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration properties.
 * Bound to the 'jwt' prefix in application.yml.
 * No provider names are embedded here; values are injected via environment variables.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long expirationMs,
        long refreshExpirationMs
) {}
