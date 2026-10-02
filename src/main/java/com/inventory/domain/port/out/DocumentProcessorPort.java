package com.inventory.domain.port.out;

import java.util.List;

/**
 * Output port — Document processing and RAG retrieval abstraction.
 *
 * Implementations handle text extraction from files (PDF, images, plain text),
 * chunking, embedding and storage into a vector store, and similarity retrieval.
 */
public interface DocumentProcessorPort {

    /**
     * Processes a file: extracts text, chunks it, embeds each chunk and
     * stores them under the given store key for later retrieval.
     *
     * @param storeKey   Logical key to scope the vector store (e.g., session ID).
     * @param filename   Original filename (used to detect file type).
     * @param content    Raw file bytes.
     */
    void processAndStore(String storeKey, String filename, byte[] content);

    /**
     * Retrieves the top-k most relevant text chunks for the given query.
     *
     * @param storeKey   The vector store key to search in.
     * @param query      The query text (typically the user's instruction).
     * @param topK       Maximum number of chunks to return.
     * @return           Ordered list of relevant text chunks.
     */
    List<String> retrieveRelevant(String storeKey, String query, int topK);

    /**
     * Removes all stored chunks for a given key (e.g., on session expiry).
     *
     * @param storeKey  The vector store key to clear.
     */
    void clearStore(String storeKey);
}
