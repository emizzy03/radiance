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

@DataJpaTest
class PurchaseRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Test
    void findDailyReport_aggregatesQuantityAndRevenuePerDayAndProduct() {
        Product lamp = productRepository.save(new Product("Lamp", "d", new BigDecimal("10.00"), 100, "l.png"));
        Product mug = productRepository.save(new Product("Mug", "d", new BigDecimal("5.00"), 100, "m.png"));

        LocalDate day1 = LocalDate.of(2026, 1, 1);
        LocalDate day2 = LocalDate.of(2026, 1, 2);
        Instant ts = Instant.now();

        // Day 1: lamp x2 (@10) + lamp x3 (@10) => qty 5, revenue 50; mug x4 (@5) => qty 4, revenue 20
        purchaseRepository.save(new Purchase(lamp, 2, new BigDecimal("10.00"), day1, ts));
        purchaseRepository.save(new Purchase(lamp, 3, new BigDecimal("10.00"), day1, ts));
        purchaseRepository.save(new Purchase(mug, 4, new BigDecimal("5.00"), day1, ts));
        // Day 2: lamp x1 (@10) => qty 1, revenue 10
        purchaseRepository.save(new Purchase(lamp, 1, new BigDecimal("10.00"), day2, ts));

        List<DailyPurchaseReport> report = purchaseRepository.findDailyReport();

        // 3 groups: (day1, lamp), (day1, mug), (day2, lamp)
        assertThat(report).hasSize(3);

        DailyPurchaseReport day1Lamp = report.stream()
                .filter(r -> r.getDate().equals(day1) && r.getProductName().equals("Lamp"))
                .findFirst().orElseThrow();
        assertThat(day1Lamp.getTotalQuantity()).isEqualTo(5L);
        assertThat(day1Lamp.getTotalRevenue()).isEqualByComparingTo("50.00");

        DailyPurchaseReport day1Mug = report.stream()
                .filter(r -> r.getDate().equals(day1) && r.getProductName().equals("Mug"))
                .findFirst().orElseThrow();
        assertThat(day1Mug.getTotalQuantity()).isEqualTo(4L);
        assertThat(day1Mug.getTotalRevenue()).isEqualByComparingTo("20.00");

        DailyPurchaseReport day2Lamp = report.stream()
                .filter(r -> r.getDate().equals(day2))
                .findFirst().orElseThrow();
        assertThat(day2Lamp.getTotalQuantity()).isEqualTo(1L);
        assertThat(day2Lamp.getTotalRevenue()).isEqualByComparingTo("10.00");
    }
}
