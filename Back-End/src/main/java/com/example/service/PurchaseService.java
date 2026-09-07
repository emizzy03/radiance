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
        if (product.getQuantity() != null && product.getQuantity() < quantity) {
            throw new IllegalArgumentException("Insufficient stock for product: " + product.getName());
        }

        Instant now = Instant.now();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        Purchase purchase = new Purchase(product, quantity, product.getPrice(), today, now);

        if (product.getQuantity() != null) {
            product.setQuantity(product.getQuantity() - quantity);
            productRepository.save(product);
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
