package com.inventory.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.domain.port.out.AiAnalyticsPort;
import com.inventory.infrastructure.config.AgentsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Adaptador para analíticas usando la configuración del agente L2 o L3 (o un modelo genérico).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnalyticsAdapter implements AiAnalyticsPort {

    private final HttpAiClient aiClient;
    private final AgentsProperties agentsProperties;
    private final ObjectMapper objectMapper;

    @Override
    public String analyzeMetrics(String metricsDataJson, String userPrompt) {
        log.info("Generando análisis de métricas con IA...");
        String systemInstruction = "Eres un experto analista de datos de ventas. "
                + "Se te proporcionarán métricas en formato JSON. ";

        if (userPrompt != null && !userPrompt.isBlank()) {
            systemInstruction += "El usuario tiene una pregunta específica sobre estos datos. "
                    + "Usa los datos para responder a su pregunta de forma clara y concisa en español.";
        } else {
            systemInstruction += "Escribe un breve resumen de las ventas, destacando tendencias, "
                    + "productos con bajo stock y recomendaciones. Mantenlo en español y conciso.";
        }

        List<ChatMessage> messages = List.of(
                ChatMessage.system(systemInstruction),
                ChatMessage.user("Métricas JSON: " + metricsDataJson + 
                        (userPrompt != null ? "\nPregunta del usuario: " + userPrompt : ""))
        );

        // Usar L2 por defecto para métricas para buen balance entre velocidad y razonamiento
        AgentsProperties.AgentLevel level = agentsProperties.levels().get("L2");
        if (level == null) {
            log.warn("Nivel L2 no configurado, intentando L1");
            level = agentsProperties.levels().get("L1");
        }

        try {
            HttpAiClient.AiResponse response = aiClient.chatCompletion(messages, level);
            return response.content();
        } catch (Exception e) {
            log.error("Error al comunicarse con la IA para métricas: {}", e.getMessage());
            return "No se pudo generar el análisis en este momento debido a un error de conexión con la IA.";
        }
    }

    @Override
    public List<String> generateProductInsights(String productsJson) {
        // Implementación futura
        return List.of("Insight 1", "Insight 2");
    }
}
