package com.inventory.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for the product agent endpoint.
 *
 * @param sessionId   Optional. Existing session ID for multi-turn conversations.
 * @param instruction Required. Natural-language instruction from the user.
 */
public record AgentRequestDto(
        String sessionId,

        @NotBlank(message = "instruction must not be blank")
        @Size(max = 4000, message = "instruction must not exceed 4000 characters")
        String instruction
) {}
