package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * After checkout the storefront reloads stock via {@code GET /api/products}
 * (the list), not GET-by-id. A list projection that omitted or froze
 * {@code quantity} would leave sold-out badges and cart max-qty stale.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogListReflectsCheckoutStockHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publicCatalogList_showsDecrementedQuantityAfterCheckout() throws Exception {
        String productJson =
                "{\"name\":\"Cov21ListStockMug\",\"description\":\"d\",\"price\":8.00,\"quantity\":6,\"image\":\"/media/m.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated());

        MvcResult list = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode products = objectMapper.readTree(list.getResponse().getContentAsString());
        JsonNode match = null;
        for (JsonNode product : products) {
            if (product.path("id").asLong() == id) {
                match = product;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(match, "catalog list must still include the purchased SKU");
        org.junit.jupiter.api.Assertions.assertEquals(4, match.get("quantity").asInt());
        org.junit.jupiter.api.Assertions.assertEquals("Cov21ListStockMug", match.get("name").asText());
    }
}
