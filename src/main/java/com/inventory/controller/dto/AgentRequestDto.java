package com.inventory.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de petición para el endpoint del agente de productos.
 *
 * @param sessionId   Opcional. ID de sesión existente para conversaciones multi-turno.
 * @param instruction Requerido. Instrucción en lenguaje natural del usuario.
 */
public record AgentRequestDto(
        String sessionId,

        @NotBlank(message = "instruction must not be blank")
        @Size(max = 4000, message = "instruction must not exceed 4000 characters")
        String instruction
) {}
