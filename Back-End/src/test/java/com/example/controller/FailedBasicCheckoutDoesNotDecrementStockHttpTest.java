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
 * Public checkout is {@code permitAll}, but presenting invalid HTTP Basic must
 * still fail closed: a wrong password or unknown username is 401, stock is
 * unchanged, and the daily report gets no row. Anonymous checkout of the same
 * SKU afterwards still succeeds. Complements shopper Basic checkout (PR #22)
 * and wrong-password {@code /api/auth/me} (PR #22). Catches treating a failed
 * AuthenticationFilter as anonymous success on {@code POST /api/purchases}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FailedBasicCheckoutDoesNotDecrementStockHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void failedBasic_onPublicCheckout_doesNotDecrementStockOrAddReportRow() throws Exception {
        String create = "{\"name\":\"Cov33 Fail Basic Sku\",\"description\":\"d\","
                + "\"price\":6.00,\"quantity\":3,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/purchases")
                        .with(httpBasic("admin", "wrong-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/purchases")
                        .with(httpBasic("cov33nobody", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));

        JsonNode reportAfterFailed = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        Assertions.assertNull(rowForProduct(reportAfterFailed, id),
                "failed Basic must not insert a daily-report row");

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(6.00));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));

        JsonNode reportAfterAnonymous = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode row = rowForProduct(reportAfterAnonymous, id);
        Assertions.assertNotNull(row, "anonymous checkout after failed Basic must still record the sale");
        Assertions.assertEquals(1, row.path("totalQuantity").asInt());
        Assertions.assertEquals(0, new BigDecimal(row.path("totalRevenue").asText())
                .compareTo(new BigDecimal("6.00")));
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
