package com.inventory.controller.dto;

import com.inventory.domain.model.ExtractionResult;
import com.inventory.domain.port.in.ManageProductPort;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the product agent endpoint.
 *
 * @param sessionId       Use this ID in the next request if status is AWAITING_INPUT.
 * @param status          COMPLETE | AWAITING_INPUT | FAILED
 * @param operation       CREATE or UPDATE (when detected)
 * @param product         Extracted product data (partially or fully filled)
 * @param missingFields   Required fields still missing (when AWAITING_INPUT)
 * @param question        Question to show the user (when AWAITING_INPUT)
 * @param savedProductId  The persisted product ID (only when COMPLETE)
 * @param agentLevel      Which agent level produced this result (L1, L2, L3)
 * @param confidence      Agent's confidence score [0.0, 1.0]
 */
public record AgentResponseDto(
        String sessionId,
        String status,
        String operation,
        ProductData product,
        List<String> missingFields,
        String question,
        UUID savedProductId,
        String agentLevel,
        double confidence
) {
    public record ProductData(
            String sku,
            String name,
            String description,
            String imageUrl,
            BigDecimal price,
            Integer stock
    ) {}

    public static AgentResponseDto from(ManageProductPort.AgentUseCaseResult result) {
        ExtractionResult e = result.extraction();
        ProductData productData = new ProductData(
                e.sku(), e.name(), e.description(), e.imageUrl(), e.price(), e.stock());
        return new AgentResponseDto(
                result.sessionId(),
                e.status().name(),
                e.operation() != null ? e.operation().name() : null,
                productData,
                e.missingFields(),
                e.question(),
                result.savedProductId(),
                e.agentLevel(),
                e.confidence()
        );
    }
}
