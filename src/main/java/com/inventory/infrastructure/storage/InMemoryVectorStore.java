package com.inventory.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory vector store using cosine similarity for retrieval.
 *
 * Each document is stored as a (text, embedding) pair under a scoped key.
 * Suitable for development and moderate loads. Replace with a dedicated
 * vector database (pgvector, Qdrant, Chroma, etc.) for production.
 *
 * Scoped by a string key (e.g., session ID) so different conversations
 * don't bleed into each other.
 */
@Slf4j
@Component
public class InMemoryVectorStore {

    /** storeKey → list of (chunk text, embedding vector) */
    private final Map<String, List<VectorDocument>> store = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    public void add(String storeKey, String text, float[] embedding) {
        store.computeIfAbsent(storeKey, k -> Collections.synchronizedList(new ArrayList<>()))
             .add(new VectorDocument(text, embedding));
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns the top-k most similar documents to the query embedding,
     * ordered by descending cosine similarity.
     */
    public List<String> findTopK(String storeKey, float[] queryEmbedding, int k) {
        List<VectorDocument> docs = store.getOrDefault(storeKey, List.of());
        if (docs.isEmpty()) return List.of();

        return docs.stream()
                .map(doc -> new AbstractMap.SimpleEntry<>(doc.text(), cosineSimilarity(queryEmbedding, doc.embedding())))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(k)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    public void clear(String storeKey) {
        store.remove(storeKey);
        log.debug("Vector store cleared for key: {}", storeKey);
    }

    public int size(String storeKey) {
        return store.getOrDefault(storeKey, List.of()).size();
    }

    // -------------------------------------------------------------------------
    // Cosine similarity
    // -------------------------------------------------------------------------

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }
}
