package com.inventory.infrastructure.storage;

/** A text chunk paired with its embedding vector, stored in the vector store. */
public record VectorDocument(String text, float[] embedding) {}
