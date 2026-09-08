package com.example.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.example.entity.Product;

/**
 * Isolated tests for the atomic conditional stock decrement used by checkout.
 * This query is the oversell guard: it must succeed only when enough stock
 * remains, drain exact remaining stock to zero, and leave quantity unchanged
 * when the update matches no row.
 */
@DataJpaTest
class ProductRepositoryStockTest {

    @Autowired
    private ProductRepository productRepository;

    private Product saveProduct(int quantity) {
        return productRepository.save(
                new Product("Stocked", "d", new BigDecimal("9.99"), quantity, "s.png"));
    }

    @Test
    void decrementStock_succeedsWhenEnoughStockRemains() {
        Product product = saveProduct(10);

        int updated = productRepository.decrementStock(product.getId(), 4);

        assertThat(updated).isEqualTo(1);
        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(6);
    }

    @Test
    void decrementStock_exactRemainingStockDrainsToZero() {
        Product product = saveProduct(5);

        int updated = productRepository.decrementStock(product.getId(), 5);

        assertThat(updated).isEqualTo(1);
        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(0);
    }

    @Test
    void decrementStock_insufficientStockUpdatesNoRowAndLeavesQuantity() {
        Product product = saveProduct(3);

        int updated = productRepository.decrementStock(product.getId(), 5);

        assertThat(updated).isEqualTo(0);
        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(3);
    }

    @Test
    void decrementStock_unknownProductUpdatesNoRow() {
        int updated = productRepository.decrementStock(999_999L, 1);
        assertThat(updated).isEqualTo(0);
    }
}
