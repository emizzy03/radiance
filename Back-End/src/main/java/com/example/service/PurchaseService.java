package com.example.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dto.DailyPurchaseReport;
import com.example.entity.Product;
import com.example.entity.Purchase;
import com.example.repository.ProductRepository;
import com.example.repository.PurchaseRepository;

/**
 * Service operations for recording purchases and producing daily aggregates.
 */
@Service
public class PurchaseService {

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private ProductRepository productRepository;

    /**
     * Records a purchase of the given product/quantity, snapshotting the current
     * product price and decrementing available stock.
     */
    @Transactional
    public Purchase recordPurchase(Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

        Instant now = Instant.now();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        // Snapshot the price at purchase time before any bulk update clears the
        // persistence context.
        Purchase purchase = new Purchase(product, quantity, product.getPrice(), today, now);

        // A null quantity means the product has untracked/unlimited stock, so we
        // skip the decrement entirely. Otherwise perform an atomic, DB-enforced
        // conditional decrement: the repository update only succeeds when enough
        // stock remains, so concurrent checkouts cannot oversell and quantity can
        // never go negative.
        if (product.getQuantity() != null) {
            int updated = productRepository.decrementStock(productId, quantity);
            if (updated == 0) {
                throw new IllegalArgumentException("Insufficient stock for product: " + product.getName());
            }
        }
        return purchaseRepository.save(purchase);
    }

    public BigDecimal lineTotal(Purchase purchase) {
        return purchase.getUnitPrice().multiply(BigDecimal.valueOf(purchase.getQuantity()));
    }

    public List<DailyPurchaseReport> getDailyReport() {
        return purchaseRepository.findDailyReport();
    }
}
