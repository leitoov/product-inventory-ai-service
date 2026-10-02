package com.inventory.domain.port.out;

import com.inventory.domain.model.Product;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port — Product persistence abstraction.
 * Implemented by the JPA adapter in the infrastructure layer.
 */
public interface ProductRepositoryPort {

    Product save(Product product);

    java.util.List<Product> findAll();

    Optional<Product> findById(UUID id);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    void deleteById(UUID id);
}
