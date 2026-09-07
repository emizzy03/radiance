package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Aggregated daily purchase totals for a single product on a single day.
 * Used by the ADMIN reporting endpoint.
 */
public class DailyPurchaseReport {

    private final LocalDate date;
    private final Long productId;
    private final String productName;
    private final Long totalQuantity;
    private final BigDecimal totalRevenue;

    public DailyPurchaseReport(LocalDate date, Long productId, String productName,
                               Long totalQuantity, BigDecimal totalRevenue) {
        this.date = date;
        this.productId = productId;
        this.productName = productName;
        this.totalQuantity = totalQuantity;
        this.totalRevenue = totalRevenue;
    }

    public LocalDate getDate() {
        return date;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Long getTotalQuantity() {
        return totalQuantity;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }
}
