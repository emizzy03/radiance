package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

/**
 * Missing-id and null-price guards for catalog writes. Complementary to
 * {@link ProductServiceTest} so open coverage PRs can merge without colliding.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceGuardTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void createProduct_rejectsNullPrice() {
        Product invalid = new Product("Lamp", "desc", null, 10, "lamp.png");
        assertThatThrownBy(() -> productService.createProduct(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price");
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_unknownIdReturnsNull() {
        Product patch = new Product();
        patch.setPrice(new BigDecimal("1.00"));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(productService.updateProduct(99L, patch)).isNull();
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deleteProduct_unknownIdReturnsFalse() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThat(productService.deleteProduct(99L)).isFalse();
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void deleteProduct_existingReturnsTrue() {
        when(productRepository.existsById(1L)).thenReturn(true);

        assertThat(productService.deleteProduct(1L)).isTrue();
        verify(productRepository).deleteById(1L);
    }
}
