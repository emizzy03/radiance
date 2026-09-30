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
 * Storefront shoppers retry a smaller quantity after an oversized checkout
 * 400. The failed request must leave stock and the daily report unchanged;
 * the later valid buy must decrement and report only that sale. Complements
 * a single insufficient-stock HTTP 400 (PR #24) and oversized-then-remainder
 * after an admin inventory PATCH (PR #25).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OversizedThenRetryCheckoutHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void oversizedCheckout_thenSmallerRetry_sellsOnlyTheSuccessfulQuantity() throws Exception {
        String create = "{\"name\":\"Cov34 Retry Sku\",\"description\":\"d\","
                + "\"price\":4.00,\"quantity\":5,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MvcResult oversized = mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":10}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode error = objectMapper.readTree(oversized.getResponse().getContentAsString());
        Assertions.assertTrue(error.path("message").asText().toLowerCase().contains("insufficient"),
                "oversized checkout must explain insufficient stock");

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5));

        JsonNode reportAfterFail = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        Assertions.assertNull(rowForProduct(reportAfterFail, id),
                "failed oversized checkout must not insert a daily-report row");

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(4.00));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));

        JsonNode reportAfterRetry = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode row = rowForProduct(reportAfterRetry, id);
        Assertions.assertNotNull(row, "successful retry must appear on the daily report");
        Assertions.assertEquals(2, row.path("totalQuantity").asInt());
        Assertions.assertEquals(0, new BigDecimal(row.path("totalRevenue").asText())
                .compareTo(new BigDecimal("8.00")));
        Assertions.assertEquals("Cov34 Retry Sku", row.path("productName").asText());
    }

    private static JsonNode rowForProduct(JsonNode report, long productId) {
        for (JsonNode row : report) {
            if (row.path("productId").asLong() == productId) {
                return row;
            }
        }
        return null;
    }
}
