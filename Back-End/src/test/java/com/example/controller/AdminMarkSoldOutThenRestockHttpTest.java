package com.example.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

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
 * Admin Edit→Save can mark a SKU sold out by PATCHing {@code quantity: 0}
 * without deleting it. The storefront still lists qty-0 items, checkout must
 * reject them without a report row, and a later restock must allow a sale.
 * Complements create-with-qty-0 (PR #15) and restock after checkout drain
 * (PR #16).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminMarkSoldOutThenRestockHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminPatchQuantityZero_blocksCheckoutUntilRestock() throws Exception {
        String create = "{\"name\":\"Cov29 Sold Out\",\"description\":\"d\","
                + "\"price\":4.00,\"quantity\":5,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/products/" + id)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0))
                .andExpect(jsonPath("$.name").value("Cov29 Sold Out"))
                .andExpect(jsonPath("$.price").value(4.00));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0));

        MvcResult list = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();
        boolean listed = false;
        for (JsonNode row : objectMapper.readTree(list.getResponse().getContentAsString())) {
            if (row.path("id").asLong() == id) {
                listed = true;
                org.junit.jupiter.api.Assertions.assertEquals(0, row.path("quantity").asInt());
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(listed, "qty-0 SKU must remain on the public catalog");

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Insufficient stock")));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0));

        MvcResult reportBefore = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode row : objectMapper.readTree(reportBefore.getResponse().getContentAsString())) {
            org.junit.jupiter.api.Assertions.assertNotEquals(id, row.path("productId").asLong(),
                    "sold-out checkout must not add a report row");
        }

        mockMvc.perform(patch("/api/products/" + id)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(4.00));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));

        MvcResult reportAfter = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode match = null;
        for (JsonNode row : objectMapper.readTree(reportAfter.getResponse().getContentAsString())) {
            if (row.path("productId").asLong() == id) {
                match = row;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(match, "restocked checkout missing from daily report");
        org.junit.jupiter.api.Assertions.assertEquals(1, match.path("totalQuantity").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                0, new BigDecimal(match.path("totalRevenue").asText()).compareTo(new BigDecimal("4.00")));
    }
}
