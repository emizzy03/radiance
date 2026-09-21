package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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
 * The admin dashboard records a purchase with Basic then immediately reloads
 * {@code GET /api/purchases/daily-report}. Checkout succeeding is not enough:
 * the report row the table and chart render must include this SKU's id, name,
 * quantity, and snapshot revenue.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardPurchaseShowsOnDailyReportHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void adminBasicRecordPurchase_appearsOnDailyReportWithSnapshotRevenue() throws Exception {
        String createJson = "{\"name\":\"Cov25ReportCandle\",\"description\":\"d\","
                + "\"price\":7.50,\"quantity\":9,\"image\":\"/media/candle.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/purchases")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(7.50))
                .andExpect(jsonPath("$.quantity").value(4));

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
        org.junit.jupiter.api.Assertions.assertNotNull(match, "daily report must include the recorded SKU");
        org.junit.jupiter.api.Assertions.assertEquals("Cov25ReportCandle", match.get("productName").asText());
        org.junit.jupiter.api.Assertions.assertEquals(4L, match.get("totalQuantity").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(0, match.get("totalRevenue").decimalValue()
                .compareTo(new java.math.BigDecimal("30.00")));
        org.junit.jupiter.api.Assertions.assertTrue(match.hasNonNull("date"));
    }
}
