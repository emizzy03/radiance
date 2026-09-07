package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.entity.Product;
import com.example.entity.Purchase;
import com.example.repository.ProductRepository;
import com.example.repository.PurchaseRepository;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private PurchaseService purchaseService;

    @Test
    void recordPurchase_snapshotsPriceAndDecrementsStock() {
        Product product = new Product("Lamp", "d", new BigDecimal("12.50"), 10, "l.png");
        product.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));

        Purchase purchase = purchaseService.recordPurchase(1L, 3);

        assertThat(purchase.getUnitPrice()).isEqualByComparingTo("12.50");
        assertThat(purchase.getQuantity()).isEqualTo(3);
        assertThat(purchase.getPurchaseDate()).isNotNull();
        assertThat(purchase.getCreatedAt()).isNotNull();

        // stock decremented from 10 to 7
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        org.mockito.Mockito.verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(7);
    }

    @Test
    void recordPurchase_rejectsInsufficientStock() {
        Product product = new Product("Lamp", "d", new BigDecimal("12.50"), 2, "l.png");
        product.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> purchaseService.recordPurchase(1L, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void recordPurchase_rejectsUnknownProduct() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseService.recordPurchase(99L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void recordPurchase_rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> purchaseService.recordPurchase(1L, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
