package com.example.controller;

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
 * Daily-report revenue is {@code SUM(unitPrice * quantity)} of checkout
 * snapshots. Two sales of the same SKU at different catalog prices must
 * add both snapshots, not the live price times total quantity. Complements
 * freeze-after-purchase (PR #14) and retag-then-new-checkout (PR #23).
 */
@SpringBootTest
@AutoConfigureMockMvc
class DailyReportMixedSnapshotPricesHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dailyReport_sumsMixedSnapshotPricesForSameSku() throws Exception {
        String create = "{\"name\":\"Cov29 Mixed Price\",\"description\":\"d\","
                + "\"price\":10.00,\"quantity\":10,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(10.00))
                .andExpect(jsonPath("$.quantity").value(1));

        mockMvc.perform(patch("/api/products/" + id)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":3.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(3.50));

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(3.50))
                .andExpect(jsonPath("$.quantity").value(2));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(3.50))
                .andExpect(jsonPath("$.quantity").value(7));

        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(report.getResponse().getContentAsString());
        JsonNode match = null;
        for (JsonNode row : rows) {
            if (row.path("productId").asLong() == id) {
                match = row;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(match, "daily report missing mixed-price SKU");
        org.junit.jupiter.api.Assertions.assertEquals(3, match.path("totalQuantity").asInt());
        // 1×10.00 + 2×3.50 = 17.00 — not live 3.50×3 = 10.50, and not 10.00×3 = 30.00
        org.junit.jupiter.api.Assertions.assertEquals(
                0, new BigDecimal(match.path("totalRevenue").asText()).compareTo(new BigDecimal("17.00")));
    }
}
