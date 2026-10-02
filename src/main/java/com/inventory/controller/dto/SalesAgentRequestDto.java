package com.inventory.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record SalesAgentRequestDto(
        String sessionId,
        
        @NotBlank(message = "La instrucción no puede estar vacía")
        String instruction
) {}
