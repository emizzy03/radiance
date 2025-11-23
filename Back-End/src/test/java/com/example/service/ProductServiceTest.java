package com.example.service;

import com.example.entity.Product;
import com.example.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = new Product("Laptop", "High-performance laptop", new BigDecimal("999.99"), 10, "laptop.jpg");
        testProduct.setId(1L);
    }

    @Test
    void testGetAllProducts() {
        List<Product> products = Arrays.asList(
            testProduct,
            new Product("Mouse", "Wireless mouse", new BigDecimal("29.99"), 50, "mouse.jpg")
        );
        when(productRepository.findAll()).thenReturn(products);
        
        List<Product> result = productService.getAllProducts();
        
        assertEquals(2, result.size());
        verify(productRepository, times(1)).findAll();
    }

    @Test
    void testGetProductById_Found() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        
        Product found = productService.getProductById(1L);
        
        assertNotNull(found);
        assertEquals("Laptop", found.getName());
        verify(productRepository, times(1)).findById(1L);
    }

    @Test
    void testGetProductById_NotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());
        
        Product found = productService.getProductById(999L);
        
        assertNull(found);
        verify(productRepository, times(1)).findById(999L);
    }

    @Test
    void testCreateProduct_Success() {
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);
        
        Product created = productService.createProduct(testProduct);
        
        assertNotNull(created);
        assertEquals("Laptop", created.getName());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void testCreateProduct_EmptyName_ThrowsException() {
        Product invalidProduct = new Product("", "Description", new BigDecimal("100.00"), 10, "image.jpg");
        
        assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(invalidProduct);
        });
        
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void testCreateProduct_NullName_ThrowsException() {
        Product invalidProduct = new Product(null, "Description", new BigDecimal("100.00"), 10, "image.jpg");
        
        assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(invalidProduct);
        });
        
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void testCreateProduct_WhitespaceName_ThrowsException() {
        Product invalidProduct = new Product("   ", "Description", new BigDecimal("100.00"), 10, "image.jpg");
        
        assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(invalidProduct);
        });
        
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void testCreateProduct_NullPrice_ThrowsException() {
        Product invalidProduct = new Product("Product", "Description", null, 10, "image.jpg");
        
        assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(invalidProduct);
        });
        
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void testCreateProduct_ZeroPrice_ThrowsException() {
        Product invalidProduct = new Product("Product", "Description", BigDecimal.ZERO, 10, "image.jpg");
        
        assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(invalidProduct);
        });
        
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void testCreateProduct_NegativePrice_ThrowsException() {
        Product invalidProduct = new Product("Product", "Description", new BigDecimal("-10.00"), 10, "image.jpg");
        
        assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(invalidProduct);
        });
        
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void testCreateProduct_NullQuantity_ThrowsException() {
        Product invalidProduct = new Product("Product", "Description", new BigDecimal("100.00"), null, "image.jpg");
        
        assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(invalidProduct);
        });
        
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void testCreateProduct_NegativeQuantity_ThrowsException() {
        Product invalidProduct = new Product("Product", "Description", new BigDecimal("100.00"), -5, "image.jpg");
        
        assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(invalidProduct);
        });
        
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void testCreateProduct_ZeroQuantity_Success() {
        Product validProduct = new Product("Product", "Description", new BigDecimal("100.00"), 0, "image.jpg");
        when(productRepository.save(any(Product.class))).thenReturn(validProduct);
        
        Product created = productService.createProduct(validProduct);
        
        assertNotNull(created);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void testUpdateProduct_Success() {
        Product updateDetails = new Product("Updated Laptop", "New description", new BigDecimal("899.99"), 5, "new.jpg");
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);
        
        Product updated = productService.updateProduct(1L, updateDetails);
        
        assertNotNull(updated);
        verify(productRepository, times(1)).findById(1L);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void testUpdateProduct_NotFound() {
        Product updateDetails = new Product("Updated Laptop", "New description", new BigDecimal("899.99"), 5, "new.jpg");
        when(productRepository.findById(999L)).thenReturn(Optional.empty());
        
        Product updated = productService.updateProduct(999L, updateDetails);
        
        assertNull(updated);
        verify(productRepository, times(1)).findById(999L);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void testDeleteProduct_Success() {
        when(productRepository.existsById(1L)).thenReturn(true);
        doNothing().when(productRepository).deleteById(1L);
        
        boolean deleted = productService.deleteProduct(1L);
        
        assertTrue(deleted);
        verify(productRepository, times(1)).existsById(1L);
        verify(productRepository, times(1)).deleteById(1L);
    }

    @Test
    void testDeleteProduct_NotFound() {
        when(productRepository.existsById(999L)).thenReturn(false);
        
        boolean deleted = productService.deleteProduct(999L);
        
        assertFalse(deleted);
        verify(productRepository, times(1)).existsById(999L);
        verify(productRepository, never()).deleteById(anyLong());
    }

    @Test
    void testSearchProductsByName() {
        List<Product> products = Arrays.asList(testProduct);
        when(productRepository.findByNameContainingIgnoreCase("laptop")).thenReturn(products);
        
        List<Product> result = productService.searchProductsByName("laptop");
        
        assertEquals(1, result.size());
        assertEquals("Laptop", result.get(0).getName());
        verify(productRepository, times(1)).findByNameContainingIgnoreCase("laptop");
    }

    @Test
    void testSearchProductsByName_NoResults() {
        when(productRepository.findByNameContainingIgnoreCase("tablet")).thenReturn(Arrays.asList());
        
        List<Product> result = productService.searchProductsByName("tablet");
        
        assertTrue(result.isEmpty());
        verify(productRepository, times(1)).findByNameContainingIgnoreCase("tablet");
    }

    @Test
    void testFindProductsByPriceRange() {
        List<Product> products = Arrays.asList(testProduct);
        BigDecimal minPrice = new BigDecimal("500.00");
        BigDecimal maxPrice = new BigDecimal("1500.00");
        
        when(productRepository.findByPriceBetween(minPrice, maxPrice)).thenReturn(products);
        
        List<Product> result = productService.findProductsByPriceRange(minPrice, maxPrice);
        
        assertEquals(1, result.size());
        verify(productRepository, times(1)).findByPriceBetween(minPrice, maxPrice);
    }

    @Test
    void testFindAvailableProducts() {
        List<Product> products = Arrays.asList(testProduct);
        when(productRepository.findByQuantityGreaterThan(0)).thenReturn(products);
        
        List<Product> result = productService.findAvailableProducts();
        
        assertEquals(1, result.size());
        assertTrue(result.get(0).getQuantity() > 0);
        verify(productRepository, times(1)).findByQuantityGreaterThan(0);
    }

    @Test
    void testFindAvailableProducts_NoResults() {
        when(productRepository.findByQuantityGreaterThan(0)).thenReturn(Arrays.asList());
        
        List<Product> result = productService.findAvailableProducts();
        
        assertTrue(result.isEmpty());
        verify(productRepository, times(1)).findByQuantityGreaterThan(0);
    }
}
