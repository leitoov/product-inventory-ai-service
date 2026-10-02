package com.inventory.domain.port.out;

import com.inventory.domain.model.ExtractionResult;

import java.util.Map;

/**
 * Output port — AI product extraction agent abstraction.
 *
 * Implementations handle the escalation logic (L1 → L2 → L3) internally.
 * The application service only calls this port and receives a result.
 *
 * The port is intentionally generic: it receives plain-text prompts and
 * returns structured domain objects. No AI provider names appear here.
 */
public interface ProductAgentPort {

    /**
     * Attempts to extract product fields from the combined context.
     *
     * @param instruction    The user's natural-language instruction.
     * @param ragContext     Top-k document chunks retrieved from the vector store
     *                       (empty string if no file was uploaded).
     * @param sessionContext Accumulated context from prior conversation turns.
     * @return               Extraction result including status, fields, and missing fields.
     */
    ExtractionResult extractProduct(String instruction, String ragContext, String sessionContext);
}
