package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.entity.Product;
import com.example.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void updateProduct_partialUpdateKeepsExistingFields() {
        Product existing = new Product("Lamp", "A nice lamp", new BigDecimal("19.99"), 10, "lamp.png");
        existing.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product patch = new Product();
        patch.setPrice(new BigDecimal("24.99")); // only price supplied

        Product updated = productService.updateProduct(1L, patch);

        assertThat(updated.getPrice()).isEqualByComparingTo("24.99");
        assertThat(updated.getName()).isEqualTo("Lamp");
        assertThat(updated.getDescription()).isEqualTo("A nice lamp");
        assertThat(updated.getQuantity()).isEqualTo(10);
        assertThat(updated.getImage()).isEqualTo("lamp.png");
    }

    @Test
    void createProduct_rejectsInvalidPrice() {
        Product invalid = new Product("Lamp", "desc", new BigDecimal("0.00"), 10, "lamp.png");
        assertThatThrownBy(() -> productService.createProduct(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createProduct_rejectsEmptyName() {
        Product invalid = new Product("  ", "desc", new BigDecimal("19.99"), 10, "lamp.png");
        assertThatThrownBy(() -> productService.createProduct(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void createProduct_rejectsNegativeQuantity() {
        Product invalid = new Product("Lamp", "desc", new BigDecimal("19.99"), -1, "lamp.png");
        assertThatThrownBy(() -> productService.createProduct(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void createProduct_savesValidProduct() {
        Product valid = new Product("Lamp", "desc", new BigDecimal("19.99"), 10, "lamp.png");
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product created = productService.createProduct(valid);

        assertThat(created.getName()).isEqualTo("Lamp");
    }
}
