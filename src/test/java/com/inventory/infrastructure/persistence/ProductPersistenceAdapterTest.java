package com.inventory.infrastructure.persistence;

import com.inventory.AbstractIntegrationTest;
import com.inventory.domain.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProductPersistenceAdapterTest extends AbstractIntegrationTest {

    @Autowired
    private ProductPersistenceAdapter adapter;

    @Test
    void shouldSaveAndFindProduct() {
        // Arrange
        Product product = Product.newProduct("LAP-001", "Laptop Test", "Descripción test", null, new java.math.BigDecimal("1200.00"), 10);

        // Act
        Product saved = adapter.save(product);

        // Assert
        assertThat(saved.id()).isNotNull();
        assertThat(saved.sku()).isEqualTo("LAP-001");
        
        // Find
        Optional<Product> found = adapter.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Laptop Test");
    }

    @Test
    void shouldFindAllProducts() {
        // Arrange
        Product p1 = Product.newProduct("MOU-001", "Mouse", "A mouse", null, new java.math.BigDecimal("25.00"), 50);
        Product p2 = Product.newProduct("KEY-001", "Keyboard", "A keyboard", null, new java.math.BigDecimal("45.00"), 30);
        adapter.save(p1);
        adapter.save(p2);

        // Act
        List<Product> products = adapter.findAll();

        // Assert
        assertThat(products).isNotEmpty();
        assertThat(products).extracting(Product::sku).contains("MOU-001", "KEY-001");
    }
}
