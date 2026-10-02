package com.inventory.service;

import com.inventory.domain.model.*;
import com.inventory.domain.port.in.ManageProductPort;
import com.inventory.domain.port.out.DocumentProcessorPort;
import com.inventory.domain.port.out.ProductAgentPort;
import com.inventory.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Application service that orchestrates the agent-driven product creation/update flow.
 *
 * Responsibilities:
 *   1. Session management  — create or restore an AgentSession from the cache.
 *   2. RAG pipeline        — if a file is present, delegate to DocumentProcessorPort
 *                            and retrieve relevant chunks for the agent prompt.
 *   3. Agent call          — delegate to ProductAgentPort (escalation is internal to the adapter).
 *   4. Partial data merge  — merge each turn's extracted fields into the session.
 *   5. Persistence         — when the extraction is COMPLETE, save the product and clear the session.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAgentService implements ManageProductPort {

    private static final String SESSION_CACHE = "agentSessions";

    private final ProductAgentPort agentPort;
    private final DocumentProcessorPort documentProcessorPort;
    private final ProductRepositoryPort productRepository;
    private final CacheManager cacheManager;

    @Value("${rag.top-k-results:5}")
    private int ragTopK;

    // =========================================================================
    // Public use-case entry point
    // =========================================================================

    @Override
    @Transactional
    public AgentUseCaseResult process(String sessionId,
                                       String instruction,
                                       String filename,
                                       byte[] fileContent) {

        // 1. Restore or create session
        AgentSession session = resolveSession(sessionId);
        session.addUserMessage(instruction);

        log.info("Agent session [{}] — instruction: '{}'",
                session.getSessionId(), abbreviate(instruction));

        // 2. RAG: process uploaded file (if any) and retrieve relevant context
        String ragContext = processFileAndRetrieve(session, filename, fileContent, instruction);

        // 3. Build session context and call the agent
        String sessionContext = session.buildContextSummary();
        ExtractionResult result = agentPort.extractProduct(instruction, ragContext, sessionContext);

        log.info("Agent [{}] returned status={} confidence={} missingFields={}",
                result.agentLevel(), result.status(), result.confidence(), result.missingFields());

        // 4. Integrar datos parciales en la sesión sin importar el estado
        session.mergePartialData(result);

        if (result.needsMoreInfo()) {
            session.addAgentMessage(result.question());
            saveSession(session);
            return new AgentUseCaseResult(session.getSessionId(), result, null);
        }

        if (result.isComplete()) {
            UUID savedId = persistProduct(result, session);
            evictSession(session.getSessionId());
            documentProcessorPort.clearStore(session.getVectorStoreKey());
            return new AgentUseCaseResult(session.getSessionId(), result, savedId);
        }

        // FAILED — mantenemos la sesión viva para que el usuario pueda reintentar
        session.addAgentMessage("No pude procesar tu solicitud. Por favor, intenta reformularla.");
        saveSession(session);
        return new AgentUseCaseResult(session.getSessionId(), result, null);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private AgentSession resolveSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            Cache cache = getCache();
            Cache.ValueWrapper wrapper = cache.get(sessionId);
            if (wrapper != null && wrapper.get() instanceof AgentSession existing) {
                log.debug("Restored existing session [{}]", sessionId);
                return existing;
            }
            log.warn("Session [{}] not found or expired. Starting a new session.", sessionId);
        }
        AgentSession newSession = new AgentSession(UUID.randomUUID().toString());
        log.debug("Created new session [{}]", newSession.getSessionId());
        return newSession;
    }

    private void saveSession(AgentSession session) {
        getCache().put(session.getSessionId(), session);
    }

    private void evictSession(String sessionId) {
        getCache().evict(sessionId);
    }

    private Cache getCache() {
        Cache cache = cacheManager.getCache(SESSION_CACHE);
        if (cache == null) throw new IllegalStateException("Cache '" + SESSION_CACHE + "' not configured.");
        return cache;
    }

    private String processFileAndRetrieve(AgentSession session, String filename,
                                           byte[] fileContent, String query) {
        if (filename == null || fileContent == null || fileContent.length == 0) return "";
        log.info("Session [{}] — processing file: {} ({} bytes)",
                session.getSessionId(), filename, fileContent.length);
        documentProcessorPort.processAndStore(session.getVectorStoreKey(), filename, fileContent);
        List<String> chunks = documentProcessorPort.retrieveRelevant(
                session.getVectorStoreKey(), query, ragTopK);
        String context = String.join("\n---\n", chunks);
        log.debug("Session [{}] — RAG retrieved {} chunks", session.getSessionId(), chunks.size());
        return context;
    }

    private UUID persistProduct(ExtractionResult result, AgentSession session) {
        String sku         = coalesce(result.sku(),         session.getPartialSku());
        String name        = coalesce(result.name(),        session.getPartialName());
        String description = coalesce(result.description(), session.getPartialDescription());
        String imageUrl    = coalesce(result.imageUrl(),    session.getPartialImageUrl());
        BigDecimal price   = result.price() != null ? result.price() : parseSafe(session.getPartialPrice());
        Integer stock      = result.stock() != null ? result.stock() : parseSafeInt(session.getPartialStock());

        if (result.operation() == ProductOperation.UPDATE) {
            return productRepository.findBySku(sku)
                    .map(existing -> {
                        Product updated = existing.withUpdatedFields(name, description, imageUrl, price, stock);
                        return productRepository.save(updated).id();
                    })
                    .orElseGet(() -> {
                        log.warn("UPDATE for SKU '{}' not found — creating instead.", sku);
                        return productRepository.save(
                                Product.newProduct(sku, name, description, imageUrl, price, stock)).id();
                    });
        }

        Product saved = productRepository.save(
                Product.newProduct(sku, name, description, imageUrl, price, stock));
        log.info("Product created — id={} sku={}", saved.id(), saved.sku());
        return saved.id();
    }

    private static String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private static BigDecimal parseSafe(String value) {
        if (value == null || value.isBlank()) return null;
        try { return new BigDecimal(value.replaceAll("[^\\d.]", "")); }
        catch (NumberFormatException e) { return null; }
    }

    private static Integer parseSafeInt(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.parseInt(value.replaceAll("[^\\d]", "")); }
        catch (NumberFormatException e) { return null; }
    }

    private static String abbreviate(String text) {
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }
}
