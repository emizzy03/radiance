package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Verifies the public customer-facing storefront static assets are served and
 * that the public browse -> checkout flow the storefront relies on still works.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StorefrontPageTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void storefrontIndexIsPubliclyServed() throws Exception {
        mockMvc.perform(get("/shop/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void storefrontAssetsArePubliclyServed() throws Exception {
        mockMvc.perform(get("/shop/app.js")).andExpect(status().isOk());
        mockMvc.perform(get("/shop/styles.css")).andExpect(status().isOk());
    }

    @Test
    void storefrontDirectoryForwardsToIndex() throws Exception {
        mockMvc.perform(get("/shop/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/shop/index.html"));
    }

    @Test
    void publicCatalogBrowseWorks() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void publicCheckoutStillWorks() throws Exception {
        // Seed a product as admin, then check out anonymously via the storefront path.
        String productJson =
                "{\"name\":\"Candle\",\"description\":\"d\",\"price\":12.00,\"quantity\":5,\"image\":\"/media/x.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated());
    }

    @Test
    void publicCheckout_insufficientStockIsBadRequestAndLeavesStockUnchanged() throws Exception {
        String productJson =
                "{\"name\":\"Vase\",\"description\":\"d\",\"price\":20.00,\"quantity\":1,\"image\":\"/media/x.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient stock")));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(1));
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        mockMvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound());
    }
}
