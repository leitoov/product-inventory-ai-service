package com.inventory.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result produced by the AI agent after attempting to extract product data
 * from the user's instruction and/or uploaded file context.
 *
 * Status transitions:
 *   COMPLETE       → all required fields found; ready to persist.
 *   AWAITING_INPUT → one or more required fields missing; ask the user.
 *   FAILED         → agent could not produce a usable result after all escalation levels.
 */
public record ExtractionResult(
        Status status,

        /** Detected intent: create a new product or update an existing one. */
        ProductOperation operation,

        /** Partially or fully extracted field values. */
        String sku,
        String name,
        String description,
        String imageUrl,
        BigDecimal price,
        Integer stock,

        /** Required fields that the agent could not find. */
        List<String> missingFields,

        /** Natural-language question to ask the user when AWAITING_INPUT. */
        String question,

        /** Agent confidence score [0.0, 1.0]. */
        double confidence,

        /** Name of the agent level that produced this result (L1, L2, L3). */
        String agentLevel,

        /** Raw JSON response from the AI for debugging. */
        String rawAgentResponse
) {
    public enum Status { COMPLETE, AWAITING_INPUT, FAILED }

    public boolean isComplete() { return status == Status.COMPLETE; }
    public boolean needsMoreInfo() { return status == Status.AWAITING_INPUT; }

    /** Validates that all required fields are present and returns missing field names. */
    public static List<String> detectMissingRequired(String sku, String name,
                                                      BigDecimal price, Integer stock) {
        List<String> missing = new ArrayList<>();
        if (sku == null || sku.isBlank())   missing.add("sku");
        if (name == null || name.isBlank())  missing.add("name");
        if (price == null)                   missing.add("price");
        if (stock == null)                   missing.add("stock");
        return Collections.unmodifiableList(missing);
    }

    /** Convenience builder-style factory for a complete extraction. */
    public static ExtractionResult complete(ProductOperation op,
                                             String sku, String name, String description,
                                             String imageUrl, BigDecimal price, Integer stock,
                                             double confidence, String agentLevel, String raw) {
        return new ExtractionResult(Status.COMPLETE, op, sku, name, description, imageUrl,
                price, stock, List.of(), null, confidence, agentLevel, raw);
    }

    /** Convenience factory for a partial extraction requiring follow-up. */
    public static ExtractionResult awaitingInput(ProductOperation op,
                                                  String sku, String name, String description,
                                                  String imageUrl, BigDecimal price, Integer stock,
                                                  List<String> missing, String question,
                                                  double confidence, String agentLevel, String raw) {
        return new ExtractionResult(Status.AWAITING_INPUT, op, sku, name, description, imageUrl,
                price, stock, missing, question, confidence, agentLevel, raw);
    }

    /** Factory for a total failure. */
    public static ExtractionResult failed(String reason) {
        return new ExtractionResult(Status.FAILED, null, null, null, null, null,
                null, null, List.of(), reason, 0.0, "none", null);
    }
}
