package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.entity.Product;
import com.example.repository.ProductRepository;

/**
 * Regression tests for the checkout oversell race condition. The public
 * {@code POST /api/purchases} flow used to read-check-write stock, so two
 * concurrent checkouts could both pass the "enough stock?" check and oversell.
 * The fix performs an atomic, DB-enforced conditional decrement, so only as many
 * orders as there is stock can ever succeed and quantity can never go negative.
 */
@SpringBootTest
class PurchaseConcurrencyTest {

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void recordPurchase_rejectsOrderLargerThanStock_andLeavesStockUnchanged() {
        Product product = productRepository.save(
                new Product("Lamp", "d", new BigDecimal("12.50"), 3, "l.png"));

        assertThatThrownBy(() -> purchaseService.recordPurchase(product.getId(), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(3);
    }

    @Test
    void concurrentCheckouts_neverOversellAndStockNeverGoesNegative() throws Exception {
        int initialStock = 20;
        int concurrentOrders = 50; // deliberately more requests than available stock
        Product product = productRepository.save(
                new Product("Candle", "d", new BigDecimal("9.99"), initialStock, "c.png"));
        Long productId = product.getId();

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrentOrders);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < concurrentOrders; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    purchaseService.recordPurchase(productId, 1);
                    successes.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    // Insufficient stock is the expected outcome once stock is exhausted.
                    failures.incrementAndGet();
                } catch (Exception e) {
                    // Any other failure (e.g. lock contention) should not silently pass.
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown(); // release all workers at once to maximise contention
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // Exactly `initialStock` orders may succeed, the rest must be rejected.
        assertThat(successes.get()).isEqualTo(initialStock);
        assertThat(failures.get()).isEqualTo(concurrentOrders - initialStock);

        // Stock is fully drained but never negative.
        Product reloaded = productRepository.findById(productId).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(0);
        assertThat(reloaded.getQuantity()).isGreaterThanOrEqualTo(0);
    }
}
