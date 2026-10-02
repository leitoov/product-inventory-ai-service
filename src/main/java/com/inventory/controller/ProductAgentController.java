package com.inventory.controller;

import com.inventory.controller.dto.AgentResponseDto;
import com.inventory.domain.port.in.ManageProductPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST controller for the AI-powered product agent endpoint.
 *
 * Multi-turn conversation flow:
 *   1. POST /api/products/agent — initial request (instruction + optional file)
 *      Response: { sessionId, status: "AWAITING_INPUT", question, missingFields }
 *   2. POST /api/products/agent — follow-up (sessionId + instruction with missing data)
 *      Response: { sessionId, status: "COMPLETE", savedProductId }
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/products/agent")
@RequiredArgsConstructor
@Tag(name = "Product Agent", description = "AI-powered product creation and update via natural language")
public class ProductAgentController {

    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB

    private final ManageProductPort manageProductPort;

    @Operation(
        summary = "Process a product instruction via AI agent",
        description = "Accepts a natural-language instruction and an optional file (PDF, image, CSV, TXT). "
            + "Returns COMPLETE (200) when all fields are found, or AWAITING_INPUT (202) when more info is needed. "
            + "Use the returned sessionId in follow-up requests."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "COMPLETE — product saved",
            content = @Content(schema = @Schema(implementation = AgentResponseDto.class))),
        @ApiResponse(responseCode = "202", description = "AWAITING_INPUT — more information needed"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "413", description = "File exceeds 20 MB"),
        @ApiResponse(responseCode = "422", description = "FAILED — all agent levels exhausted"),
        @ApiResponse(responseCode = "403", description = "Insufficient role (ADMIN required)")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentResponseDto> processInstruction(

            @Parameter(description = "Existing session ID (required for follow-up turns)")
            @RequestPart(value = "sessionId", required = false)
            String sessionId,

            @Parameter(description = "Natural-language instruction (required)", required = true)
            @RequestPart("instruction")
            @NotBlank(message = "instruction must not be blank")
            @Size(max = 4000)
            String instruction,

            @Parameter(description = "Optional file: PDF catalog, product image, CSV or plain text")
            @RequestPart(value = "file", required = false)
            MultipartFile file

    ) throws IOException {

        log.info("Agent request — sessionId={} instruction='{}' file={}",
                sessionId, abbreviate(instruction),
                file != null ? file.getOriginalFilename() + " (" + file.getSize() + " bytes)" : "none");

        if (file != null && !file.isEmpty() && file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }

        String filename    = (file != null && !file.isEmpty()) ? file.getOriginalFilename() : null;
        byte[] fileContent = (file != null && !file.isEmpty()) ? file.getBytes() : null;

        ManageProductPort.AgentUseCaseResult result =
                manageProductPort.process(sessionId, instruction, filename, fileContent);

        AgentResponseDto response = AgentResponseDto.from(result);

        return switch (result.extraction().status()) {
            case COMPLETE       -> ResponseEntity.ok(response);
            case AWAITING_INPUT -> ResponseEntity.accepted().body(response);
            case FAILED         -> ResponseEntity.unprocessableEntity().body(response);
        };
    }

    private static String abbreviate(String text) {
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }
}
