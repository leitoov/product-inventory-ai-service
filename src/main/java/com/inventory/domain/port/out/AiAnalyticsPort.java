package com.inventory.domain.port.out;

import java.util.List;

/**
 * Puerto de salida — Abstracción de analíticas con IA.
 *
 * Las implementaciones viven en infrastructure/ai/.
 * El dominio nunca conoce qué proveedor de IA está activo.
 */
public interface AiAnalyticsPort {

    /**
     * Genera un resumen en lenguaje natural o responde a una pregunta sobre los datos de ventas.
     *
     * @param metricsDataJson JSON con las métricas a analizar.
     * @param userPrompt      Pregunta o instrucción opcional del usuario. Si es nulo, genera un resumen general.
     * @return                Texto legible con la respuesta de la IA.
     */
    String analyzeMetrics(String metricsDataJson, String userPrompt);

    /**
     * Genera insights accionables para una lista de productos.
     *
     * @param productsJson JSON con la lista de productos.
     * @return             Lista de cadenas de texto con insights.
     */
    List<String> generateProductInsights(String productsJson);
}
