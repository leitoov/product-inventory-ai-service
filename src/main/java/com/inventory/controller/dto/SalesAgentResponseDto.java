package com.inventory.controller.dto;

import com.inventory.domain.model.SalesSession;

import java.util.List;

public record SalesAgentResponseDto(
        String sessionId,
        String message,
        List<SalesSession.CartItem> cart,
        String status, // "CONTINUE" o "CHECKOUT"
        String quotePdfBase64 // Presente solo si status es CHECKOUT
) {}
