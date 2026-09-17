package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * A storefront cart can hold a SKU the admin later deletes. Checkout of that
 * id must be HTTP 400 with a {@code message} the shop UI flashes, and must
 * not insert a daily-report row for a product that is gone.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutDeletedProductHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void checkout_deletedProductIsBadRequestAndDoesNotAppearOnReport() throws Exception {
        String json = "{\"name\":\"Cov21DeletedBowl\",\"description\":\"d\",\"price\":11.00,"
                + "\"quantity\":4,\"image\":\"/media/bowl.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/products/" + id).with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(report.getResponse().getContentAsString());
        for (JsonNode row : rows) {
            org.junit.jupiter.api.Assertions.assertNotEquals(
                    id, row.path("productId").asLong(),
                    "daily report must not gain a row for a deleted SKU");
        }
    }
}
