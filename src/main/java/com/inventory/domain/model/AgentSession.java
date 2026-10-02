package com.inventory.domain.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the state of an ongoing multi-turn agent conversation.
 *
 * A session is created when a user sends an initial request and
 * persists until the product is saved (COMPLETE) or the session expires.
 *
 * The accumulated context (prior messages + extracted partial data) is
 * passed to the agent on every follow-up turn so it maintains memory.
 */
public class AgentSession {

    private final String sessionId;
    private final OffsetDateTime createdAt;
    private OffsetDateTime lastActivityAt;

    /** History of user messages for multi-turn context. */
    private final List<String> conversationHistory = new ArrayList<>();

    /** Partial product data accumulated across turns. */
    private String partialSku;
    private String partialName;
    private String partialDescription;
    private String partialImageUrl;
    private String partialPrice;
    private String partialStock;

    /** RAG document store key (scoped to this session). */
    private final String vectorStoreKey;

    public AgentSession(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = OffsetDateTime.now();
        this.lastActivityAt = this.createdAt;
        this.vectorStoreKey = "session:" + sessionId;
    }

    // -------------------------------------------------------------------------
    // Mutators
    // -------------------------------------------------------------------------

    public void addUserMessage(String message) {
        conversationHistory.add("USER: " + message);
        this.lastActivityAt = OffsetDateTime.now();
    }

    public void addAgentMessage(String message) {
        conversationHistory.add("AGENT: " + message);
        this.lastActivityAt = OffsetDateTime.now();
    }

    /** Merges partial data from the latest extraction result into the session. */
    public void mergePartialData(ExtractionResult result) {
        if (result.sku() != null && !result.sku().isBlank())         this.partialSku = result.sku();
        if (result.name() != null && !result.name().isBlank())       this.partialName = result.name();
        if (result.description() != null)                            this.partialDescription = result.description();
        if (result.imageUrl() != null)                               this.partialImageUrl = result.imageUrl();
        if (result.price() != null)                                  this.partialPrice = result.price().toPlainString();
        if (result.stock() != null)                                  this.partialStock = String.valueOf(result.stock());
        this.lastActivityAt = OffsetDateTime.now();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Builds a summarized context string to inject into the next agent prompt. */
    public String buildContextSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Accumulated product data so far:\n");
        if (partialSku != null)         sb.append("  - SKU: ").append(partialSku).append("\n");
        if (partialName != null)        sb.append("  - Name: ").append(partialName).append("\n");
        if (partialDescription != null) sb.append("  - Description: ").append(partialDescription).append("\n");
        if (partialPrice != null)       sb.append("  - Price: ").append(partialPrice).append("\n");
        if (partialStock != null)       sb.append("  - Stock: ").append(partialStock).append("\n");
        if (partialImageUrl != null)    sb.append("  - Image URL: ").append(partialImageUrl).append("\n");

        if (!conversationHistory.isEmpty()) {
            sb.append("\nConversation history (last 6 messages):\n");
            int start = Math.max(0, conversationHistory.size() - 6);
            conversationHistory.subList(start, conversationHistory.size())
                               .forEach(msg -> sb.append("  ").append(msg).append("\n"));
        }
        return sb.toString();
    }

    public String getSessionId()          { return sessionId; }
    public OffsetDateTime getCreatedAt()  { return createdAt; }
    public OffsetDateTime getLastActivityAt() { return lastActivityAt; }
    public String getVectorStoreKey()     { return vectorStoreKey; }
    public String getPartialSku()         { return partialSku; }
    public String getPartialName()        { return partialName; }
    public String getPartialDescription() { return partialDescription; }
    public String getPartialImageUrl()    { return partialImageUrl; }
    public String getPartialPrice()       { return partialPrice; }
    public String getPartialStock()       { return partialStock; }
}
