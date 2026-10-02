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
 * Controlador para el endpoint del agente de productos con IA.
 *
 * Flujo de conversación:
 * 1. POST /api/products/agent — peticion inicial (instruccion + archivo
 * opcional)
 * Respuesta: { sessionId, status: "AWAITING_INPUT", question, missingFields }
 * 2. POST /api/products/agent — seguimiento (sessionId + instruccion con datos
 * faltantes)
 * Respuesta: { sessionId, status: "COMPLETE", savedProductId }
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/products/agent")
@RequiredArgsConstructor
@Tag(name = "Product Agent", description = "AI-powered crea o actualiza productos via agente")
public class ProductAgentController {

    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB

    private final ManageProductPort manageProductPort;

    @Operation(summary = "Procesar instrucción de producto vía agente de IA", description = "Acepta una instrucción en lenguaje natural y un archivo opcional (PDF, imagen, CSV, TXT). "
            + "Retorna COMPLETE (200) cuando todos los campos son encontrados, o AWAITING_INPUT (202) cuando se necesita más información. "
            + "Usa el sessionId devuelto en las peticiones de seguimiento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "COMPLETE — producto guardado", content = @Content(schema = @Schema(implementation = AgentResponseDto.class))),
            @ApiResponse(responseCode = "202", description = "AWAITING_INPUT — se necesita más información"),
            @ApiResponse(responseCode = "400", description = "Petición inválida"),
            @ApiResponse(responseCode = "413", description = "El archivo excede los 20 MB"),
            @ApiResponse(responseCode = "422", description = "FAILED — todos los niveles de agente fallaron"),
            @ApiResponse(responseCode = "403", description = "Rol insuficiente (se requiere ADMIN)")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentResponseDto> processInstruction(

            @Parameter(description = "ID de sesión existente (requerido para turnos de seguimiento)") @RequestPart(value = "sessionId", required = false) String sessionId,

            @Parameter(description = "Instrucción en lenguaje natural (requerida)", required = true) @RequestPart("instruction") @NotBlank(message = "instruction must not be blank") @Size(max = 4000) String instruction,

            @Parameter(description = "Archivo opcional: catálogo PDF, imagen de producto, CSV o texto plano") @RequestPart(value = "file", required = false) MultipartFile file

    ) throws IOException {

        log.info("Agent request — sessionId={} instruction='{}' file={}",
                sessionId, abbreviate(instruction),
                file != null ? file.getOriginalFilename() + " (" + file.getSize() + " bytes)" : "none");

        if (file != null && !file.isEmpty() && file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }

        String filename = (file != null && !file.isEmpty()) ? file.getOriginalFilename() : null;
        byte[] fileContent = (file != null && !file.isEmpty()) ? file.getBytes() : null;

        ManageProductPort.AgentUseCaseResult result = manageProductPort.process(sessionId, instruction, filename,
                fileContent);

        AgentResponseDto response = AgentResponseDto.from(result);

        return switch (result.extraction().status()) {
            case COMPLETE -> ResponseEntity.ok(response);
            case AWAITING_INPUT -> ResponseEntity.accepted().body(response);
            case FAILED -> ResponseEntity.unprocessableEntity().body(response);
        };
    }

    private static String abbreviate(String text) {
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }
}
