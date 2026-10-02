package com.inventory.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Core domain entity for a product.
 * Pure Java — no JPA or framework annotations.
 */
public record Product(
        UUID id,
        String sku,
        String name,
        String description,
        String imageUrl,
        BigDecimal price,
        Integer stock,
        OffsetDateTime lastUpdatedAt,
        OffsetDateTime createdAt
) {
    /** Creates a new Product without an id (to be assigned by persistence). */
    public static Product newProduct(String sku, String name, String description,
                                     String imageUrl, BigDecimal price, Integer stock) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Product(null, sku, name, description, imageUrl, price, stock, now, now);
    }

    /** Returns a copy with an updated lastUpdatedAt timestamp. */
    public Product withUpdatedFields(String name, String description,
                                     String imageUrl, BigDecimal price, Integer stock) {
        return new Product(this.id, this.sku, name, description, imageUrl,
                price, stock, OffsetDateTime.now(), this.createdAt);
    }
}
