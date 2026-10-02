package com.inventory.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.domain.model.ExtractionResult;
import com.inventory.domain.model.ProductOperation;
import com.inventory.domain.port.out.ProductAgentPort;
import com.inventory.infrastructure.config.AgentLevelConfig;
import com.inventory.infrastructure.config.AgentsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AI agent adapter that implements the escalation pattern: L1 → L2 → L3.
 *
 * Escalation triggers:
 *   - Agent level throws AgentTimeoutException or AgentCallException (unavailable/error)
 *   - Agent returns a result with confidence below the level's minConfidence threshold
 *
 * Best-practice pattern applied:
 *   "Start with the cheapest/fastest resource; only spend more capacity when needed."
 *
 * Each level can target a different model or even a different provider endpoint.
 * No provider names appear in this class — all driven by {@link AgentLevelConfig}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductAgentAdapter implements ProductAgentPort {

    // =========================================================================
    // System prompt — provider-agnostic, structured output enforced
    // =========================================================================
    private static final String SYSTEM_PROMPT = """
            Eres un agente de extracción de datos de productos. Tu ÚNICA función es extraer
            información del producto a partir de las instrucciones del usuario y cualquier contexto provisto.
            
            Campos requeridos: sku, name, price (numérico), stock (entero)
            Campos opcionales: description, imageUrl
            
            Reglas:
            - Detecta si el usuario quiere CREAR (CREATE) un nuevo producto o ACTUALIZAR (UPDATE) uno existente.
            - Si TODOS los campos requeridos están presentes, responde con status "COMPLETE".
            - Si falta algún campo requerido, responde con status "AWAITING_INPUT" e
              incluye una pregunta concisa (en español) pidiendo solo la información que falta.
            - NUNCA inventes o adivines datos. Si no encuentras un campo, déjalo como null.
            - Responde ÚNICAMENTE con un JSON válido. Sin comillas invertidas, sin markdown, sin texto extra.
            
            Formato de respuesta (estricto):
            {
              "status": "COMPLETE" | "AWAITING_INPUT",
              "operation": "CREATE" | "UPDATE",
              "product": {
                "sku":         "string o null",
                "name":        "string o null",
                "description": "string o null",
                "imageUrl":    "string o null",
                "price":       numero_o_null,
                "stock":       entero_o_null
              },
              "missingFields": ["campo1", "campo2"],
              "question":     "string o null",
              "confidence":   0.95
            }
            """;

    private final AgentsProperties agentsProperties;
    private final HttpAiClient httpAiClient;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // Port implementation — escalation loop
    // =========================================================================

    @Override
    public ExtractionResult extractProduct(String instruction,
                                            String ragContext,
                                            String sessionContext) {

        List<AgentLevelConfig> levels = agentsProperties.levels();
        if (levels == null || levels.isEmpty()) {
            log.error("No hay niveles de agente configurados en ai.agents.levels");
            return ExtractionResult.failed("No hay niveles de agente configurados.");
        }

        List<String> failures = new ArrayList<>();

        for (AgentLevelConfig level : levels) {
            log.info("→ Trying agent level [{}] (model: {}, timeout: {}ms, minConfidence: {})",
                    level.name(), level.model(), level.timeoutMs(), level.minConfidence());

            for (int attempt = 1; attempt <= level.maxRetries(); attempt++) {
                try {
                    ExtractionResult result = callLevel(level, instruction, ragContext, sessionContext);

                    if (result.confidence() >= level.minConfidence()) {
                        log.info("✓ Agent [{}] succeeded on attempt {}/{} — confidence: {}",
                                level.name(), attempt, level.maxRetries(), result.confidence());
                        return result;
                    }

                    log.warn("⚠ Agent [{}] attempt {}/{} — confidence {} below threshold {}. {}",
                            level.name(), attempt, level.maxRetries(),
                            result.confidence(), level.minConfidence(),
                            attempt < level.maxRetries() ? "Retrying same level..." : "Escalating...");

                } catch (AgentTimeoutException e) {
                    String msg = "Agent [" + level.name() + "] attempt " + attempt + "/" + level.maxRetries()
                                 + " timed out after " + level.timeoutMs() + "ms.";
                    log.warn("⏱ " + msg);
                    failures.add(msg);
                    break; // timeout → escalate immediately, no point retrying same level

                } catch (AgentCallException e) {
                    String msg = "Agent [" + level.name() + "] attempt " + attempt + "/"
                                 + level.maxRetries() + " failed: " + e.getMessage();
                    log.error("✗ " + msg);
                    failures.add(msg);
                    if (attempt == level.maxRetries()) break; // exhausted retries → escalate
                }
            }

            log.warn("↑ Escalating from [{}] to next level...", level.name());
        }

        // All levels exhausted
        String summary = "Todos los niveles de agente fallaron. Errores:\n" + String.join("\n", failures);
        log.error(summary);
        return ExtractionResult.failed(summary);
    }

    // =========================================================================
    // Single level call
    // =========================================================================

    private ExtractionResult callLevel(AgentLevelConfig level,
                                        String instruction,
                                        String ragContext,
                                        String sessionContext) {

        String userContent = buildUserPrompt(instruction, ragContext, sessionContext);

        List<ChatMessage> messages = List.of(
                ChatMessage.system(SYSTEM_PROMPT),
                ChatMessage.user(userContent)
        );

        String rawResponse = httpAiClient.chatCompletion(level, messages);
        log.debug("[{}] Raw response: {}", level.name(), rawResponse);

        return parseResponse(rawResponse, level.name());
    }

    // =========================================================================
    // Prompt & response
    // =========================================================================

    private String buildUserPrompt(String instruction, String ragContext, String sessionContext) {
        StringBuilder sb = new StringBuilder();

        if (!ragContext.isBlank()) {
            sb.append("=== DOCUMENT CONTEXT (from uploaded file via RAG) ===\n");
            sb.append(ragContext).append("\n\n");
        }

        if (!sessionContext.isBlank()) {
            sb.append("=== CONVERSATION HISTORY ===\n");
            sb.append(sessionContext).append("\n\n");
        }

        sb.append("=== USER INSTRUCTION ===\n");
        sb.append(instruction);

        return sb.toString();
    }

    private ExtractionResult parseResponse(String raw, String agentLevel) {
        try {
            // Strip markdown code fences if the model ignores instructions
            String json = raw.strip()
                             .replaceAll("^```json\\s*", "")
                             .replaceAll("^```\\s*", "")
                             .replaceAll("```$", "")
                             .strip();

            JsonNode root = objectMapper.readTree(json);

            String statusStr    = root.path("status").asText("AWAITING_INPUT");
            String operationStr = root.path("operation").asText("CREATE");
            double confidence   = root.path("confidence").asDouble(0.5);

            JsonNode product    = root.path("product");
            String sku          = nullableText(product, "sku");
            String name         = nullableText(product, "name");
            String description  = nullableText(product, "description");
            String imageUrl     = nullableText(product, "imageUrl");
            BigDecimal price    = nullableDecimal(product, "price");
            Integer stock       = nullableInt(product, "stock");

            List<String> missingFields = new ArrayList<>();
            root.path("missingFields").forEach(n -> missingFields.add(n.asText()));

            String question = root.path("question").isNull() ? null : root.path("question").asText();

            ProductOperation operation = "UPDATE".equalsIgnoreCase(operationStr)
                    ? ProductOperation.UPDATE : ProductOperation.CREATE;

            if ("COMPLETE".equalsIgnoreCase(statusStr) && missingFields.isEmpty()) {
                List<String> stillMissing = ExtractionResult.detectMissingRequired(sku, name, price, stock);
                if (!stillMissing.isEmpty()) {
                    // Model said COMPLETE but fields are actually missing — treat as AWAITING_INPUT
                    log.warn("[{}] El modelo retornó COMPLETE pero los campos {} son nulos. Degradando a AWAITING_INPUT.",
                            agentLevel, stillMissing);
                    String autoQuestion = "¿Podrías proporcionar la siguiente información faltante? "
                                         + String.join(", ", stillMissing);
                    return ExtractionResult.awaitingInput(operation, sku, name, description, imageUrl,
                            price, stock, stillMissing, autoQuestion, confidence * 0.5, agentLevel, raw);
                }
                return ExtractionResult.complete(operation, sku, name, description, imageUrl,
                        price, stock, confidence, agentLevel, raw);
            }

            if (question == null && !missingFields.isEmpty()) {
                question = "Could you provide the following information? "
                           + String.join(", ", missingFields);
            }

            return ExtractionResult.awaitingInput(operation, sku, name, description, imageUrl,
                    price, stock, missingFields, question, confidence, agentLevel, raw);

        } catch (Exception e) {
            log.error("Fallo al parsear respuesta del agente [{}]: {}", agentLevel, e.getMessage());
            throw new AgentCallException(agentLevel, "Respuesta JSON inválida: " + e.getMessage());
        }
    }

    // =========================================================================
    // JSON helpers
    // =========================================================================

    private static String nullableText(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return (n.isNull() || n.isMissingNode() || n.asText().isBlank()) ? null : n.asText().strip();
    }

    private static BigDecimal nullableDecimal(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isNull() || n.isMissingNode()) return null;
        try { return new BigDecimal(n.asText()); }
        catch (NumberFormatException e) { return null; }
    }

    private static Integer nullableInt(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isNull() || n.isMissingNode()) return null;
        try { return n.asInt(); }
        catch (Exception e) { return null; }
    }
}
