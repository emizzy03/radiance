package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Assertions;
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
 * Catalog prices are {@code decimal(10,2)}. Buying 2 of a $9.90 SKU must
 * snapshot {@code unitPrice} 9.90 and report {@code totalRevenue} 19.80, not a
 * binary-double drift such as 19.799999. Complements 9.99 cent SUM (PR #18)
 * and $0.01×3 (PR #24). Catches storing or aggregating revenue as {@code double}
 * / {@code float} in {@code Purchase.unitPrice} or the daily-report JPQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScaleTwoPriceCheckoutReportHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void checkout_scaleTwoPriceTimesQuantity_reportsExactRevenue() throws Exception {
        String create = "{\"name\":\"Cov33 Scale Two Sku\",\"description\":\"d\","
                + "\"price\":9.90,\"quantity\":5,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode product = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = product.get("id").asLong();
        Assertions.assertEquals(0, new BigDecimal(product.get("price").asText())
                .compareTo(new BigDecimal("9.90")));

        MvcResult purchased = mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode purchase = objectMapper.readTree(purchased.getResponse().getContentAsString());
        Assertions.assertEquals(2, purchase.path("quantity").asInt());
        Assertions.assertEquals(0, new BigDecimal(purchase.path("unitPrice").asText())
                .compareTo(new BigDecimal("9.90")));

        JsonNode catalog = objectMapper.readTree(
                mockMvc.perform(get("/api/products/" + id))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        Assertions.assertEquals(3, catalog.path("quantity").asInt());
        Assertions.assertEquals(0, new BigDecimal(catalog.path("price").asText())
                .compareTo(new BigDecimal("9.90")));

        JsonNode report = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode row = null;
        for (JsonNode candidate : report) {
            if (candidate.path("productId").asLong() == id) {
                row = candidate;
                break;
            }
        }
        Assertions.assertNotNull(row, "daily report must include the $9.90 SKU");
        Assertions.assertEquals("Cov33 Scale Two Sku", row.path("productName").asText());
        Assertions.assertEquals(2, row.path("totalQuantity").asInt());
        Assertions.assertEquals(0, new BigDecimal(row.path("totalRevenue").asText())
                .compareTo(new BigDecimal("19.80")));
    }
}
