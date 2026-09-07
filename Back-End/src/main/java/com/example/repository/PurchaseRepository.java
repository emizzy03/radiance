package com.example.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.dto.DailyPurchaseReport;
import com.example.entity.Purchase;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    /**
     * Aggregates purchases per day and per product, returning the total quantity
     * sold and the total revenue for each (day, product) pair.
     */
    @Query("SELECT new com.example.dto.DailyPurchaseReport("
            + "p.purchaseDate, pr.id, pr.name, SUM(p.quantity), SUM(p.unitPrice * p.quantity)) "
            + "FROM Purchase p JOIN p.product pr "
            + "GROUP BY p.purchaseDate, pr.id, pr.name "
            + "ORDER BY p.purchaseDate DESC, pr.name ASC")
    List<DailyPurchaseReport> findDailyReport();
}
