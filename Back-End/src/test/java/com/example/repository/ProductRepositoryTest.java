package com.example.repository;

import com.example.entity.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    private Product product1;
    private Product product2;
    private Product product3;

    @BeforeEach
    void setUp() {
        product1 = new Product("Laptop", "High-performance laptop", new BigDecimal("999.99"), 10, "laptop.jpg");
        product2 = new Product("Mouse", "Wireless mouse", new BigDecimal("29.99"), 50, "mouse.jpg");
        product3 = new Product("Keyboard", "Mechanical keyboard", new BigDecimal("149.99"), 0, "keyboard.jpg");
        
        entityManager.persist(product1);
        entityManager.persist(product2);
        entityManager.persist(product3);
        entityManager.flush();
    }

    @Test
    void testFindByNameContainingIgnoreCase_Found() {
        List<Product> found = productRepository.findByNameContainingIgnoreCase("laptop");
        
        assertEquals(1, found.size());
        assertEquals("Laptop", found.get(0).getName());
    }

    @Test
    void testFindByNameContainingIgnoreCase_CaseInsensitive() {
        List<Product> found = productRepository.findByNameContainingIgnoreCase("MOUSE");
        
        assertEquals(1, found.size());
        assertEquals("Mouse", found.get(0).getName());
    }

    @Test
    void testFindByNameContainingIgnoreCase_PartialMatch() {
        List<Product> found = productRepository.findByNameContainingIgnoreCase("key");
        
        assertEquals(1, found.size());
        assertEquals("Keyboard", found.get(0).getName());
    }

    @Test
    void testFindByNameContainingIgnoreCase_NotFound() {
        List<Product> found = productRepository.findByNameContainingIgnoreCase("tablet");
        
        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByPriceBetween() {
        List<Product> found = productRepository.findByPriceBetween(
            new BigDecimal("20.00"), 
            new BigDecimal("100.00")
        );
        
        assertEquals(1, found.size());
        assertEquals("Mouse", found.get(0).getName());
    }

    @Test
    void testFindByPriceBetween_IncludesExactBounds() {
        List<Product> found = productRepository.findByPriceBetween(
            new BigDecimal("29.99"), 
            new BigDecimal("149.99")
        );
        
        assertEquals(2, found.size());
    }

    @Test
    void testFindByPriceBetween_NoResults() {
        List<Product> found = productRepository.findByPriceBetween(
            new BigDecimal("1000.00"), 
            new BigDecimal("2000.00")
        );
        
        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByQuantityGreaterThan() {
        List<Product> found = productRepository.findByQuantityGreaterThan(0);
        
        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(p -> p.getName().equals("Laptop")));
        assertTrue(found.stream().anyMatch(p -> p.getName().equals("Mouse")));
    }

    @Test
    void testFindByQuantityGreaterThan_HighThreshold() {
        List<Product> found = productRepository.findByQuantityGreaterThan(20);
        
        assertEquals(1, found.size());
        assertEquals("Mouse", found.get(0).getName());
    }

    @Test
    void testFindByQuantityGreaterThan_NoResults() {
        List<Product> found = productRepository.findByQuantityGreaterThan(100);
        
        assertTrue(found.isEmpty());
    }

    @Test
    void testSaveProduct() {
        Product newProduct = new Product("Monitor", "4K Monitor", new BigDecimal("399.99"), 15, "monitor.jpg");
        Product saved = productRepository.save(newProduct);
        
        assertNotNull(saved.getId());
        assertEquals("Monitor", saved.getName());
        assertEquals(new BigDecimal("399.99"), saved.getPrice());
    }

    @Test
    void testUpdateProduct() {
        product1.setPrice(new BigDecimal("899.99"));
        product1.setQuantity(5);
        
        Product updated = productRepository.save(product1);
        
        assertEquals(new BigDecimal("899.99"), updated.getPrice());
        assertEquals(5, updated.getQuantity());
    }

    @Test
    void testDeleteProduct() {
        Long productId = product1.getId();
        productRepository.delete(product1);
        
        var found = productRepository.findById(productId);
        assertFalse(found.isPresent());
    }

    @Test
    void testFindAll() {
        var products = productRepository.findAll();
        
        assertTrue(products.size() >= 3);
    }

    @Test
    void testFindById() {
        var found = productRepository.findById(product1.getId());
        
        assertTrue(found.isPresent());
        assertEquals("Laptop", found.get().getName());
    }
}
