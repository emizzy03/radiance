package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        // Atomic conditional decrement succeeds (1 row updated).
        when(productRepository.decrementStock(1L, 3)).thenReturn(1);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));

        Purchase purchase = purchaseService.recordPurchase(1L, 3);

        assertThat(purchase.getUnitPrice()).isEqualByComparingTo("12.50");
        assertThat(purchase.getQuantity()).isEqualTo(3);
        assertThat(purchase.getPurchaseDate()).isNotNull();
        assertThat(purchase.getCreatedAt()).isNotNull();

        // Stock is decremented via the atomic conditional update, not a read-modify-write save.
        org.mockito.Mockito.verify(productRepository).decrementStock(1L, 3);
        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.never()).save(any(Product.class));
    }

    @Test
    void recordPurchase_rejectsInsufficientStock() {
        Product product = new Product("Lamp", "d", new BigDecimal("12.50"), 2, "l.png");
        product.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        // The DB-enforced conditional update matches no row when stock is insufficient.
        when(productRepository.decrementStock(1L, 5)).thenReturn(0);

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
        assertThatThrownBy(() -> purchaseService.recordPurchase(1L, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> purchaseService.recordPurchase(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordPurchase_skipsDecrementWhenStockIsUntracked() {
        // A null product quantity means unlimited/untracked stock: checkout must
        // still snapshot the price, but must not call the conditional decrement.
        Product product = new Product("Lamp", "d", new BigDecimal("12.50"), null, "l.png");
        product.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));

        Purchase purchase = purchaseService.recordPurchase(1L, 3);

        assertThat(purchase.getUnitPrice()).isEqualByComparingTo("12.50");
        assertThat(purchase.getQuantity()).isEqualTo(3);
        org.mockito.Mockito.verify(productRepository, never()).decrementStock(anyLong(), anyInt());
        org.mockito.Mockito.verify(productRepository, never()).save(any(Product.class));
    }
}
