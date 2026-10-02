package com.inventory.domain.port.in;

import com.inventory.domain.model.ExtractionResult;

/**
 * Input port — Product agent use case.
 *
 * This is the only entry point into the agent flow.
 * Called by the REST controller; implemented by the application service.
 */
public interface ManageProductPort {

    /**
     * Processes a user instruction (and optional file bytes) to create or update a product.
     *
     * @param sessionId   Existing session ID for multi-turn conversations, or null for new sessions.
     * @param instruction Natural-language instruction from the user.
     * @param filename    Name of the uploaded file, or null if no file was provided.
     * @param fileContent Raw bytes of the uploaded file, or null if no file was provided.
     * @return            Extraction result with status, product data and session ID.
     */
    AgentUseCaseResult process(String sessionId, String instruction,
                                String filename, byte[] fileContent);

    /**
     * Result returned to the controller after each agent turn.
     *
     * @param sessionId       Session ID (use this for follow-up requests).
     * @param extraction      The agent's extraction result.
     * @param savedProductId  Non-null only when status is COMPLETE and the product was persisted.
     */
    record AgentUseCaseResult(
            String sessionId,
            ExtractionResult extraction,
            java.util.UUID savedProductId
    ) {}
}
