package com.example.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByNameContainingIgnoreCase(String name);

    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    List<Product> findByQuantityGreaterThan(Integer quantity);

    /**
     * Atomically decrement stock only when enough is available. The
     * {@code WHERE p.quantity >= :qty} guard makes the check-and-decrement a
     * single DB statement, so concurrent checkouts cannot oversell stock and
     * quantity can never go negative. Returns the number of rows updated: 1 on
     * success, 0 when there was insufficient stock.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.quantity = p.quantity - :qty WHERE p.id = :id AND p.quantity >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

}
