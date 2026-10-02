package com.inventory.domain.port.in;

import java.util.List;
import java.util.Map;

/**
 * Puerto de entrada — Casos de uso de métricas de ventas e insights de IA.
 * Implementado por la capa de aplicación (service).
 */
public interface SalesMetricsPort {
    
    /**
     * Obtiene métricas filtradas, opcionalmente respondiendo a una pregunta del usuario mediante IA.
     * 
     * @param startDate   Fecha de inicio (opcional, ej. "2023-01-01")
     * @param endDate     Fecha de fin (opcional, ej. "2023-12-31")
     * @param category    Categoría para filtrar (opcional)
     * @param promptAgent Pregunta o instrucción para el agente sobre estos datos (opcional)
     * @return            Resultado con métricas en crudo y/o la respuesta de la IA.
     */
    MetricsResult getMetrics(String startDate, String endDate, String category, String promptAgent);

    record MetricsResult(
            Map<String, Object> rawData,
            String aiInsights
    ) {}
}
