package com.inventory.controller;

import com.inventory.controller.dto.SalesAgentRequestDto;
import com.inventory.controller.dto.SalesAgentResponseDto;
import com.inventory.service.SalesAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/sales/agent")
@RequiredArgsConstructor
@Tag(name = "Sales Agent", description = "Asistente de ventas basado en IA para consultas, carritos y cotizaciones")
public class SalesAgentController {

    private final SalesAgentService salesAgentService;

    @Operation(summary = "Interactuar con el agente de ventas", description = "Permite consultar stock, agregar productos al carrito y finalizar la compra (generando un PDF).")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIEWER')")
    public ResponseEntity<SalesAgentResponseDto> chatWithAgent(@RequestBody @Validated SalesAgentRequestDto request) {
        SalesAgentResponseDto response = salesAgentService.processSalesChat(request.sessionId(), request.instruction());
        return ResponseEntity.ok(response);
    }
}
