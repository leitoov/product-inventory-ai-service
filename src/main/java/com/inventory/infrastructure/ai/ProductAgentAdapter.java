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
            You are a product data extraction agent. Your ONLY function is to extract
            product information from the user's instruction and any provided document context.
            
            Required fields: sku, name, price (numeric), stock (integer)
            Optional fields: description, imageUrl
            
            Rules:
            - Detect whether the user wants to CREATE a new product or UPDATE an existing one.
            - If ALL required fields are present, respond with status "COMPLETE".
            - If any required field is missing, respond with status "AWAITING_INPUT" and
              include a concise question asking only for the missing fields.
            - NEVER invent or guess data. If a field is not found, set it to null.
            - Respond ONLY with valid JSON. No markdown fences, no extra text.
            
            Response format (strict):
            {
              "status": "COMPLETE" | "AWAITING_INPUT",
              "operation": "CREATE" | "UPDATE",
              "product": {
                "sku":         "string or null",
                "name":        "string or null",
                "description": "string or null",
                "imageUrl":    "string or null",
                "price":       number_or_null,
                "stock":       integer_or_null
              },
              "missingFields": ["field1", "field2"],
              "question":     "string or null",
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
            log.error("No agent levels configured in ai.agents.levels");
            return ExtractionResult.failed("No agent levels configured.");
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
        String summary = "All agent levels exhausted. Failures:\n" + String.join("\n", failures);
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
                    log.warn("[{}] Model returned COMPLETE but fields {} are null. Downgrading to AWAITING_INPUT.",
                            agentLevel, stillMissing);
                    String autoQuestion = "Could you provide the following missing information? "
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
            log.error("Failed to parse agent response from [{}]: {}", agentLevel, e.getMessage());
            throw new AgentCallException(agentLevel, "Invalid JSON response: " + e.getMessage());
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
