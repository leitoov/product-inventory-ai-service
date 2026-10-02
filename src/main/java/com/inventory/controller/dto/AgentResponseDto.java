package com.inventory.controller.dto;

import com.inventory.domain.model.ExtractionResult;
import com.inventory.domain.port.in.ManageProductPort;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO de respuesta para el endpoint del agente de productos.
 *
 * @param sessionId       Usa este ID en la próxima petición si el estado es AWAITING_INPUT.
 * @param status          COMPLETE | AWAITING_INPUT | FAILED
 * @param operation       CREATE o UPDATE (cuando es detectado)
 * @param product         Datos extraídos del producto (parcial o totalmente llenos)
 * @param missingFields   Campos requeridos aún faltantes (cuando es AWAITING_INPUT)
 * @param question        Pregunta a mostrar al usuario (cuando es AWAITING_INPUT)
 * @param savedProductId  ID del producto persistido (solo cuando es COMPLETE)
 * @param agentLevel      Nivel de agente que produjo este resultado (L1, L2, L3)
 * @param confidence      Puntaje de confianza del agente [0.0, 1.0]
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
