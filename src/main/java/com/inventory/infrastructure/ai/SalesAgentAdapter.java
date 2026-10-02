package com.inventory.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.domain.model.SalesAgentResult;
import com.inventory.domain.model.SalesSession;
import com.inventory.domain.port.out.SalesAgentPort;
import com.inventory.infrastructure.config.AgentLevelConfig;
import com.inventory.infrastructure.config.AgentsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesAgentAdapter implements SalesAgentPort {

    private static final String SYSTEM_PROMPT = """
            Eres un asistente de ventas para una tienda de productos. 
            Ayudas a los clientes a consultar stock, armar su carrito de compras y confirmar pedidos.
            
            Información del catálogo y stock:
            %s
            
            Contexto de la sesión actual (historial y carrito):
            %s
            
            Reglas:
            1. Solo puedes vender productos que estén en el catálogo y tengan stock suficiente.
            2. Siempre responde de manera amable en español.
            3. Si el usuario te pide agregar algo, actualiza el carrito.
            4. Si el usuario quiere confirmar o finalizar la compra (generar cotización), cambia el action a "CHECKOUT". De lo contrario, "CONTINUE".
            5. Debes responder ESTRICTAMENTE con este formato JSON:
            {
              "message": "Tu respuesta al usuario en español",
              "cart": [
                { "sku": "...", "name": "...", "quantity": 1, "price": 10.5 }
              ],
              "action": "CONTINUE" | "CHECKOUT"
            }
            No uses bloques de código markdown, responde solo con el JSON crudo.
            """;

    private final HttpAiClient aiClient;
    private final AgentsProperties agentsProperties;
    private final ObjectMapper objectMapper;

    @Override
    public SalesAgentResult processSalesInstruction(String instruction, String catalogContext, String sessionContext) {
        String prompt = String.format(SYSTEM_PROMPT, catalogContext, sessionContext);

        List<ChatMessage> messages = List.of(
                ChatMessage.system(prompt),
                ChatMessage.user(instruction)
        );

        // Usar L2 por defecto para agente de ventas
        AgentLevelConfig level = agentsProperties.levels().stream()
                .filter(l -> l.name().equals("L2"))
                .findFirst()
                .orElse(null);
        if (level == null) {
            level = agentsProperties.levels().stream()
                    .filter(l -> l.name().equals("L1"))
                    .findFirst()
                    .orElse(null);
        }

        try {
            String response = aiClient.chatCompletion(level, messages);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("Fallo al comunicarse con el agente de ventas: {}", e.getMessage());
            throw new AgentCallException(level.name(), "Fallo al procesar chat de ventas: " + e.getMessage());
        }
    }

    private SalesAgentResult parseResponse(String rawContent) throws Exception {
        String cleanJson = rawContent.replaceAll("^```json", "").replaceAll("```$", "").trim();
        JsonNode root = objectMapper.readTree(cleanJson);

        String message = root.path("message").asText();
        String actionStr = root.path("action").asText("CONTINUE");
        SalesAgentResult.Action action = "CHECKOUT".equalsIgnoreCase(actionStr) 
                ? SalesAgentResult.Action.CHECKOUT : SalesAgentResult.Action.CONTINUE;

        List<SalesSession.CartItem> cart = new ArrayList<>();
        JsonNode cartNode = root.path("cart");
        if (cartNode.isArray()) {
            for (JsonNode item : cartNode) {
                cart.add(new SalesSession.CartItem(
                        item.path("sku").asText(),
                        item.path("name").asText(),
                        item.path("quantity").asInt(),
                        BigDecimal.valueOf(item.path("price").asDouble())
                ));
            }
        }

        return new SalesAgentResult(message, cart, action);
    }
}
