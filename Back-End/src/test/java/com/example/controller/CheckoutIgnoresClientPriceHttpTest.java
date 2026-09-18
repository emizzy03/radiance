package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

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
 * Checkout must snapshot the catalog price on the server. A client that
 * injects {@code unitPrice}, {@code purchaseDate}, or {@code createdAt} into
 * the JSON body must not underpay or backdate a sale — the admin daily report
 * {@code money(totalRevenue)} would otherwise show attacker-chosen totals.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutIgnoresClientPriceHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void checkout_ignoresClientUnitPriceAndTimestamps_reportUsesCatalogPrice() throws Exception {
        String productJson =
                "{\"name\":\"Cov22PriceLamp\",\"description\":\"d\",\"price\":25.00,\"quantity\":10,\"image\":\"/media/p.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        String tampered = "{\"productId\":" + id + ",\"quantity\":2,"
                + "\"unitPrice\":0.01,\"price\":0.01,"
                + "\"purchaseDate\":\"2020-01-01\","
                + "\"createdAt\":\"2020-01-01T00:00:00Z\"}";

        MvcResult checkout = mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tampered))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(25.00))
                .andExpect(jsonPath("$.product.id").value((int) id))
                .andReturn();

        JsonNode body = objectMapper.readTree(checkout.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(
                LocalDate.now(ZoneOffset.UTC).toString(),
                body.get("purchaseDate").asText());
        org.junit.jupiter.api.Assertions.assertFalse(
                body.get("createdAt").asText().startsWith("2020-01-01"),
                "createdAt must be assigned by the server, not taken from the body");

        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(report.getResponse().getContentAsString());
        JsonNode row = null;
        for (JsonNode candidate : rows) {
            if (candidate.path("productId").asLong() == id) {
                row = candidate;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(row, "daily report must include the checkout");
        org.junit.jupiter.api.Assertions.assertEquals(2L, row.get("totalQuantity").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(
                0, new BigDecimal("50.00").compareTo(row.get("totalRevenue").decimalValue()));
    }
}
