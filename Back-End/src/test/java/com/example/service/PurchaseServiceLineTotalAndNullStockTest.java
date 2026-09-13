package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

/**
 * Money-math and untracked-stock paths that {@link PurchaseServiceTest} does
 * not cover. Complementary file so open coverage PRs can merge without
 * colliding on that class.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseServiceLineTotalAndNullStockTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private PurchaseService purchaseService;

    @Test
    void lineTotal_multipliesSnapshotPriceByQuantity() {
        Product product = new Product("Lamp", "d", new BigDecimal("12.50"), 10, "l.png");
        Purchase purchase = new Purchase(product, 3, new BigDecimal("12.50"),
                LocalDate.of(2026, 9, 13), Instant.parse("2026-09-13T10:00:00Z"));

        assertThat(purchaseService.lineTotal(purchase)).isEqualByComparingTo("37.50");
    }

    @Test
    void lineTotal_preservesFractionalCents() {
        Product product = new Product("Candle", "d", new BigDecimal("0.10"), 10, "c.png");
        Purchase purchase = new Purchase(product, 3, new BigDecimal("0.10"),
                LocalDate.of(2026, 9, 13), Instant.parse("2026-09-13T10:00:00Z"));

        assertThat(purchaseService.lineTotal(purchase)).isEqualByComparingTo("0.30");
    }

    @Test
    void recordPurchase_rejectsNullQuantity() {
        assertThatThrownBy(() -> purchaseService.recordPurchase(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity");

        verify(productRepository, never()).findById(any());
        verify(productRepository, never()).decrementStock(anyLong(), anyInt());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void recordPurchase_rejectsNegativeQuantity() {
        assertThatThrownBy(() -> purchaseService.recordPurchase(1L, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity");

        verify(productRepository, never()).findById(any());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void recordPurchase_nullProductQuantitySkipsDecrement() {
        // Untracked/unlimited stock: skip the atomic decrement entirely.
        Product product = new Product("UnlimitedLamp", "d", new BigDecimal("8.00"), null, "u.png");
        product.setId(7L);
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));

        Purchase purchase = purchaseService.recordPurchase(7L, 4);

        assertThat(purchase.getQuantity()).isEqualTo(4);
        assertThat(purchase.getUnitPrice()).isEqualByComparingTo("8.00");
        verify(productRepository, never()).decrementStock(anyLong(), anyInt());
        verify(purchaseRepository).save(any(Purchase.class));
    }
}
