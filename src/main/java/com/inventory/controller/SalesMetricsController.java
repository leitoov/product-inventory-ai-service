package com.inventory.controller;

import com.inventory.domain.port.in.SalesMetricsPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para consultar las métricas de ventas.
 * Permite filtros de fecha/categoría y la posibilidad de enviar un prompt a un agente de IA
 * para obtener análisis sobre esas métricas.
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@Tag(name = "Métricas de Ventas", description = "Endpoints para consultar métricas e insights de IA")
public class SalesMetricsController {

    private final SalesMetricsPort salesMetricsPort;

    @Operation(summary = "Obtener métricas de ventas y/o análisis de IA")
    @GetMapping("/sales")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIEWER')")
    public ResponseEntity<SalesMetricsPort.MetricsResult> getSalesMetrics(
            @Parameter(description = "Fecha de inicio (ej. 2023-01-01)") 
            @RequestParam(required = false) String startDate,
            
            @Parameter(description = "Fecha de fin (ej. 2023-12-31)") 
            @RequestParam(required = false) String endDate,
            
            @Parameter(description = "Categoría de producto") 
            @RequestParam(required = false) String category,
            
            @Parameter(description = "Prompt opcional para el agente de IA. Si se envía vacío, generará un resumen general.") 
            @RequestParam(required = false) String promptAgent
    ) {
        SalesMetricsPort.MetricsResult result = salesMetricsPort.getMetrics(startDate, endDate, category, promptAgent);
        return ResponseEntity.ok(result);
    }
}
