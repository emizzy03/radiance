package com.example.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

class ProductTest {

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(product);
        assertNull(product.getId());
        assertNull(product.getName());
        assertNull(product.getDescription());
        assertNull(product.getPrice());
        assertNull(product.getQuantity());
        assertNull(product.getImage());
    }

    @Test
    void testParameterizedConstructor() {
        BigDecimal price = new BigDecimal("99.99");
        Product testProduct = new Product("Laptop", "High-performance laptop", price, 10, "laptop.jpg");
        
        assertEquals("Laptop", testProduct.getName());
        assertEquals("High-performance laptop", testProduct.getDescription());
        assertEquals(price, testProduct.getPrice());
        assertEquals(10, testProduct.getQuantity());
        assertEquals("laptop.jpg", testProduct.getImage());
        assertNull(testProduct.getId());
    }

    @Test
    void testSettersAndGetters() {
        BigDecimal price = new BigDecimal("149.99");
        
        product.setId(1L);
        product.setName("Smartphone");
        product.setDescription("Latest model smartphone");
        product.setPrice(price);
        product.setQuantity(25);
        product.setImage("phone.jpg");

        assertEquals(1L, product.getId());
        assertEquals("Smartphone", product.getName());
        assertEquals("Latest model smartphone", product.getDescription());
        assertEquals(price, product.getPrice());
        assertEquals(25, product.getQuantity());
        assertEquals("phone.jpg", product.getImage());
    }

    @Test
    void testPriceWithDifferentScales() {
        BigDecimal price1 = new BigDecimal("10.5");
        BigDecimal price2 = new BigDecimal("10.50");
        
        product.setPrice(price1);
        assertEquals(0, product.getPrice().compareTo(price2));
    }

    @Test
    void testZeroQuantity() {
        product.setQuantity(0);
        assertEquals(0, product.getQuantity());
    }

    @Test
    void testNegativeQuantity() {
        product.setQuantity(-5);
        assertEquals(-5, product.getQuantity());
    }

    @Test
    void testLargeQuantity() {
        product.setQuantity(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, product.getQuantity());
    }

    @Test
    void testZeroPrice() {
        product.setPrice(BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO, product.getPrice());
    }

    @Test
    void testNegativePrice() {
        BigDecimal negativePrice = new BigDecimal("-10.00");
        product.setPrice(negativePrice);
        assertEquals(negativePrice, product.getPrice());
    }

    @Test
    void testLongDescription() {
        String longDescription = "A".repeat(1000);
        product.setDescription(longDescription);
        assertEquals(longDescription, product.getDescription());
    }

    @Test
    void testEmptyName() {
        product.setName("");
        assertEquals("", product.getName());
    }

    @Test
    void testNullDescription() {
        product.setDescription(null);
        assertNull(product.getDescription());
    }

    @Test
    void testUpdateAllFields() {
        // Initial values
        product.setName("Old Product");
        product.setPrice(new BigDecimal("50.00"));
        product.setQuantity(5);
        
        // Update all fields
        BigDecimal newPrice = new BigDecimal("75.00");
        product.setName("New Product");
        product.setDescription("Updated description");
        product.setPrice(newPrice);
        product.setQuantity(10);
        product.setImage("new-image.jpg");
        
        assertEquals("New Product", product.getName());
        assertEquals("Updated description", product.getDescription());
        assertEquals(newPrice, product.getPrice());
        assertEquals(10, product.getQuantity());
        assertEquals("new-image.jpg", product.getImage());
    }
}
