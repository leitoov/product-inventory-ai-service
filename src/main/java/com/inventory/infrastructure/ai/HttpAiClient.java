package com.inventory.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.inventory.infrastructure.config.AgentLevelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Generic HTTP client for chat-completion compatible AI APIs.
 *
 * Uses the standard chat-completion request/response protocol that is
 * supported by most modern AI providers. No vendor name is hardcoded.
 *
 * Message roles: "system", "user", "assistant"
 */
@Slf4j
@Component
public class HttpAiClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Shared client — connections are pooled per JVM
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Sends a chat-completion request to the configured endpoint.
     *
     * @param level    The agent level config (endpoint, key, model, timeout).
     * @param messages Ordered list of chat messages (system prompt + user turns).
     * @return         Raw text content from the first response choice.
     * @throws AgentCallException if the HTTP call fails or the response is unparseable.
     */
    public String chatCompletion(AgentLevelConfig level, List<ChatMessage> messages) {
        try {
            String body = buildRequestBody(level.model(), messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(level.endpoint() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + level.apiKey())
                    .timeout(Duration.ofMillis(level.timeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            log.debug("Sending chat-completion to [{}] model={}", level.name(), level.model());

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AgentCallException(level.name(),
                        "HTTP " + response.statusCode() + ": " + response.body());
            }

            return extractContent(response.body());

        } catch (AgentCallException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new AgentTimeoutException(level.name(), level.timeoutMs());
        } catch (Exception e) {
            throw new AgentCallException(level.name(), e.getMessage());
        }
    }

    /**
     * Sends a multimodal request that includes a base64-encoded image.
     * Used when the uploaded file is an image that the agent must inspect visually.
     */
    public String chatCompletionWithImage(AgentLevelConfig level,
                                           List<ChatMessage> messages,
                                           String base64Image,
                                           String mimeType) {
        try {
            String body = buildMultimodalRequestBody(level.model(), messages, base64Image, mimeType);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(level.endpoint() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + level.apiKey())
                    .timeout(Duration.ofMillis(level.timeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AgentCallException(level.name(),
                        "HTTP " + response.statusCode() + ": " + response.body());
            }

            return extractContent(response.body());

        } catch (AgentCallException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new AgentTimeoutException(level.name(), level.timeoutMs());
        } catch (Exception e) {
            throw new AgentCallException(level.name(), e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Request body builders
    // -------------------------------------------------------------------------

    private String buildRequestBody(String model, List<ChatMessage> messages) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);

        ArrayNode msgs = root.putArray("messages");
        for (ChatMessage msg : messages) {
            ObjectNode m = msgs.addObject();
            m.put("role", msg.role());
            m.put("content", msg.content());
        }

        root.put("temperature", 0.1);      // Low temp for structured extraction
        root.put("max_tokens", 1024);
        return objectMapper.writeValueAsString(root);
    }

    private String buildMultimodalRequestBody(String model, List<ChatMessage> messages,
                                               String base64Image, String mimeType) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);

        ArrayNode msgs = root.putArray("messages");

        // Add all prior text messages
        for (int i = 0; i < messages.size() - 1; i++) {
            ChatMessage msg = messages.get(i);
            ObjectNode m = msgs.addObject();
            m.put("role", msg.role());
            m.put("content", msg.content());
        }

        // Last user message gets the image content array
        ChatMessage lastMsg = messages.get(messages.size() - 1);
        ObjectNode lastNode = msgs.addObject();
        lastNode.put("role", lastMsg.role());
        ArrayNode contentArray = lastNode.putArray("content");

        ObjectNode textPart = contentArray.addObject();
        textPart.put("type", "text");
        textPart.put("text", lastMsg.content());

        ObjectNode imagePart = contentArray.addObject();
        imagePart.put("type", "image_url");
        ObjectNode imageUrl = imagePart.putObject("image_url");
        imageUrl.put("url", "data:" + mimeType + ";base64," + base64Image);

        root.put("temperature", 0.1);
        root.put("max_tokens", 2048);
        return objectMapper.writeValueAsString(root);
    }

    private String extractContent(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("choices")
                   .path(0)
                   .path("message")
                   .path("content")
                   .asText();
    }
}
