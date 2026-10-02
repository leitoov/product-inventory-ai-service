package com.inventory.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG pipeline configuration properties.
 * Bound to the 'rag' prefix in application.yml.
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        int chunkSize,
        int chunkOverlap,
        int topKResults
) {}
