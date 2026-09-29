package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * A double-submit of the last unit (storefront double-click, or two identical
 * {@code POST /api/purchases} bodies) must sell exactly once: first 201, second
 * 400 with Insufficient stock, public quantity stays 0, and the daily report is
 * qty 1 / $4.00 not qty 2. Complements the in-process concurrency test on main
 * and buying the remainder in one request. Catches lost conditional decrement
 * or treating checkout as retry-idempotent in the wrong direction.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DuplicateCheckoutLastUnitHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void duplicateCheckout_ofLastUnit_secondRequestDoesNotOversell() throws Exception {
        String create = "{\"name\":\"Cov33 Last Unit Sku\",\"description\":\"d\","
                + "\"price\":4.00,\"quantity\":1,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String body = "{\"productId\":" + id + ",\"quantity\":1}";

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.unitPrice").value(4.00));

        MvcResult second = mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode error = objectMapper.readTree(second.getResponse().getContentAsString());
        Assertions.assertTrue(error.path("message").asText().toLowerCase().contains("insufficient"),
                "second checkout of the last unit must explain insufficient stock");

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0));

        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/products"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode listed = null;
        for (JsonNode candidate : list) {
            if (candidate.path("id").asLong() == id) {
                listed = candidate;
                break;
            }
        }
        Assertions.assertNotNull(listed, "sold-out SKU must remain on the public catalog list");
        Assertions.assertEquals(0, listed.path("quantity").asInt());

        JsonNode report = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode row = null;
        int matches = 0;
        for (JsonNode candidate : report) {
            if (candidate.path("productId").asLong() == id) {
                row = candidate;
                matches++;
            }
        }
        Assertions.assertEquals(1, matches, "oversell must not create a second report row");
        Assertions.assertNotNull(row);
        Assertions.assertEquals(1, row.path("totalQuantity").asInt());
        Assertions.assertEquals(0, new BigDecimal(row.path("totalRevenue").asText())
                .compareTo(new BigDecimal("4.00")));
        Assertions.assertEquals("Cov33 Last Unit Sku", row.path("productName").asText());
    }
}
