package com.example.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.example.dto.DailyPurchaseReport;
import com.example.entity.Product;
import com.example.entity.Purchase;

/**
 * Daily-report query shape that {@link PurchaseRepositoryTest} does not lock:
 * fractional money, snapshot unitPrice (not live catalog price), productId, and
 * ORDER BY date DESC / name ASC.
 */
@DataJpaTest
class PurchaseRepositoryReportShapeTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Test
    void findDailyReport_sumsFractionalCentsUsingSnapshotPrice() {
        Product candle = productRepository.save(
                new Product("Candle", "d", new BigDecimal("9.99"), 100, "c.png"));

        LocalDate day = LocalDate.of(2026, 9, 14);
        Instant ts = Instant.parse("2026-09-14T10:00:00Z");
        purchaseRepository.save(new Purchase(candle, 2, new BigDecimal("9.99"), day, ts));
        purchaseRepository.save(new Purchase(candle, 1, new BigDecimal("9.99"), day, ts));

        // Later catalog retag must not change historical revenue.
        candle.setPrice(new BigDecimal("50.00"));
        productRepository.save(candle);

        List<DailyPurchaseReport> report = purchaseRepository.findDailyReport();
        DailyPurchaseReport row = report.stream()
                .filter(r -> r.getProductId().equals(candle.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(row.getProductName()).isEqualTo("Candle");
        assertThat(row.getDate()).isEqualTo(day);
        assertThat(row.getTotalQuantity()).isEqualTo(3L);
        assertThat(row.getTotalRevenue()).isEqualByComparingTo("29.97");
    }

    @Test
    void findDailyReport_ordersByDateDescThenProductNameAsc() {
        Product lamp = productRepository.save(
                new Product("Lamp", "d", new BigDecimal("10.00"), 100, "l.png"));
        Product mug = productRepository.save(
                new Product("Mug", "d", new BigDecimal("5.00"), 100, "m.png"));

        LocalDate day1 = LocalDate.of(2026, 1, 1);
        LocalDate day2 = LocalDate.of(2026, 1, 2);
        Instant ts = Instant.parse("2026-01-02T12:00:00Z");

        purchaseRepository.save(new Purchase(mug, 1, new BigDecimal("5.00"), day1, ts));
        purchaseRepository.save(new Purchase(lamp, 1, new BigDecimal("10.00"), day1, ts));
        purchaseRepository.save(new Purchase(lamp, 1, new BigDecimal("10.00"), day2, ts));

        List<DailyPurchaseReport> report = purchaseRepository.findDailyReport().stream()
                .filter(r -> r.getProductId().equals(lamp.getId()) || r.getProductId().equals(mug.getId()))
                .toList();

        assertThat(report).hasSize(3);
        assertThat(report.get(0).getDate()).isEqualTo(day2);
        assertThat(report.get(0).getProductName()).isEqualTo("Lamp");
        assertThat(report.get(1).getDate()).isEqualTo(day1);
        assertThat(report.get(1).getProductName()).isEqualTo("Lamp");
        assertThat(report.get(2).getDate()).isEqualTo(day1);
        assertThat(report.get(2).getProductName()).isEqualTo("Mug");
    }
}
