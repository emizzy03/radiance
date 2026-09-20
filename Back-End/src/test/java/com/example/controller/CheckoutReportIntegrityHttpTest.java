package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Failed checkouts must not write a daily-report row, and fractional catalog
 * prices must round-trip through the snapshot so admin revenue is not off by
 * cents. Complements stock-unchanged tests that never inspect the report.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutReportIntegrityHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void insufficientStock_doesNotCreateDailyReportRow() throws Exception {
        long id = createProduct("Cov24NoSale", "15.00", 1);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(1));

        org.junit.jupiter.api.Assertions.assertNull(
                reportRowFor(id), "rejected checkout must not appear on the daily report");
    }

    @Test
    void pennyPrice_snapshotsAndReportsExactCents() throws Exception {
        long id = createProduct("Cov24Penny", "0.01", 10);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(0.01))
                .andExpect(jsonPath("$.quantity").value(3));

        JsonNode row = reportRowFor(id);
        org.junit.jupiter.api.Assertions.assertNotNull(row, "penny SKU must appear on the daily report");
        org.junit.jupiter.api.Assertions.assertEquals("Cov24Penny", row.get("productName").asText());
        org.junit.jupiter.api.Assertions.assertEquals(3L, row.get("totalQuantity").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(
                0, new BigDecimal("0.03").compareTo(row.get("totalRevenue").decimalValue()));
    }

    private long createProduct(String name, String price, int quantity) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"description\":\"d\",\"price\":" + price
                + ",\"quantity\":" + quantity + ",\"image\":\"/media/cov24.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode reportRowFor(long productId) throws Exception {
        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(report.getResponse().getContentAsString());
        for (JsonNode row : rows) {
            if (row.path("productId").asLong() == productId) {
                return row;
            }
        }
        return null;
    }
}
