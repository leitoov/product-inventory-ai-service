package com.inventory.infrastructure.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.inventory.domain.port.out.DocumentProcessorPort;
import com.inventory.infrastructure.config.AiProviderProperties;
import com.inventory.infrastructure.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * RAG pipeline adapter: extracts text from files, chunks it, embeds chunks
 * via the configured AI provider's embedding endpoint, and stores them in the
 * in-memory vector store for later retrieval.
 *
 * Supported file types:
 *   - PDF   → Apache PDFBox text extraction
 *   - TXT / CSV / JSON → direct text read
 *   - Images (PNG, JPG, WEBP) → base64-encoded, included as image context
 *
 * Fallback embedding: if the embedding API call fails, falls back to a simple
 * TF-IDF-inspired bag-of-words float vector (lower quality but never crashes).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessorAdapter implements DocumentProcessorPort {

    private final InMemoryVectorStore vectorStore;
    private final AiProviderProperties aiProviderProperties;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // =========================================================================
    // DocumentProcessorPort
    // =========================================================================

    @Override
    public void processAndStore(String storeKey, String filename, byte[] content) {
        String mimeType = detectMimeType(filename);
        String extractedText;

        if (mimeType.startsWith("image/")) {
            // For images, store the base64 as a special marker — the agent adapter
            // will handle it as a multimodal message, not a text chunk.
            String b64 = Base64.getEncoder().encodeToString(content);
            vectorStore.add(storeKey, "__IMAGE__:" + mimeType + ":" + b64, zeroVector(1));
            log.info("Stored image file [{}] as base64 in vector store key [{}]", filename, storeKey);
            return;
        }

        try {
            extractedText = extractText(filename, mimeType, content);
        } catch (Exception e) {
            log.error("Failed to extract text from [{}]: {}", filename, e.getMessage());
            return;
        }

        if (extractedText == null || extractedText.isBlank()) {
            log.warn("No text extracted from file [{}]", filename);
            return;
        }

        log.info("Extracted ~{} chars from [{}]. Chunking and embedding...",
                extractedText.length(), filename);

        List<String> chunks = chunk(extractedText,
                ragProperties.chunkSize(), ragProperties.chunkOverlap());

        log.info("Created {} chunks for [{}]", chunks.size(), filename);

        for (String chunk : chunks) {
            float[] embedding = embed(chunk);
            vectorStore.add(storeKey, chunk, embedding);
        }

        log.info("Stored {} chunks in vector store [{}]", chunks.size(), storeKey);
    }

    @Override
    public List<String> retrieveRelevant(String storeKey, String query, int topK) {
        float[] queryEmbedding = embed(query);
        List<String> results = vectorStore.findTopK(storeKey, queryEmbedding, topK);
        log.debug("Retrieved {} chunks for query '{}' from store [{}]",
                results.size(), abbreviate(query), storeKey);
        return results;
    }

    @Override
    public void clearStore(String storeKey) {
        vectorStore.clear(storeKey);
    }

    // =========================================================================
    // Text extraction
    // =========================================================================

    private String extractText(String filename, String mimeType, byte[] content) throws Exception {
        if ("application/pdf".equals(mimeType)) {
            return extractPdf(content);
        }
        // Plain text, CSV, JSON, XML, etc.
        return new String(content, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String extractPdf(byte[] content) throws Exception {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(content))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    // =========================================================================
    // Chunking (sliding window)
    // =========================================================================

    private List<String> chunk(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end).strip());
            start += (chunkSize - overlap);
        }
        return chunks;
    }

    // =========================================================================
    // Embedding via AI provider embedding endpoint
    // =========================================================================

    private float[] embed(String text) {
        try {
            return callEmbeddingApi(text);
        } catch (Exception e) {
            log.warn("Embedding API call failed ({}). Using fallback TF-IDF vector.", e.getMessage());
            return fallbackEmbed(text);
        }
    }

    private float[] callEmbeddingApi(String text) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("input", text);
        body.put("model", aiProviderProperties.model());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiProviderProperties.endpoint() + "/embeddings"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + aiProviderProperties.apiKey())
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Embedding API returned HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode embeddingNode = root.path("data").path(0).path("embedding");

        float[] vector = new float[embeddingNode.size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) embeddingNode.get(i).asDouble();
        }
        return vector;
    }

    /**
     * Fallback embedding: simple character n-gram frequency vector (dim=256).
     * Lower quality than a real embedding model but deterministic and always works.
     */
    private float[] fallbackEmbed(String text) {
        float[] vector = new float[256];
        String lower = text.toLowerCase();
        for (int i = 0; i < lower.length() - 1; i++) {
            int idx = (lower.charAt(i) * 31 + lower.charAt(i + 1)) & 0xFF;
            vector[idx] += 1.0f;
        }
        // L2 normalize
        double norm = 0;
        for (float v : vector) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < vector.length; i++) vector[i] /= norm;
        return vector;
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private static String detectMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif"))  return "image/gif";
        return "text/plain";
    }

    private static float[] zeroVector(int dim) {
        return new float[dim];
    }

    private static String abbreviate(String text) {
        return text.length() <= 60 ? text : text.substring(0, 60) + "...";
    }
}
