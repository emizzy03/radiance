package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

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
 * Catalog names with Latin accents, CJK, and emoji must round-trip on the
 * public GET, nested checkout product, and daily-report {@code productName}
 * (the JPQL groups by {@code pr.name}). Complements HTML/quotes names (PR #32).
 * Catches a non-UTF-8 JDBC/JSON path or truncating {@code product.name}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UnicodeEmojiProductNameRoundTripHttpTest {

    private static final String NAME = "Cov35 Caf\u00e9 \u5546\u54c1 \uD83C\uDF38";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unicodeEmojiName_roundTripsOnCatalogCheckoutAndReport() throws Exception {
        String create = "{\"name\":" + objectMapper.writeValueAsString(NAME)
                + ",\"description\":\"d\",\"price\":5.50,\"quantity\":4,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(NAME))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(NAME));

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(5.50))
                .andExpect(jsonPath("$.product.id").value((int) id))
                .andExpect(jsonPath("$.product.name").value(NAME));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.name").value(NAME));

        JsonNode report = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8));
        int matches = 0;
        JsonNode row = null;
        for (JsonNode candidate : report) {
            if (candidate.path("productId").asLong() == id) {
                row = candidate;
                matches++;
            }
        }
        Assertions.assertEquals(1, matches);
        Assertions.assertNotNull(row);
        Assertions.assertEquals(NAME, row.path("productName").asText());
        Assertions.assertEquals(2, row.path("totalQuantity").asInt());
        Assertions.assertEquals(0, new BigDecimal(row.path("totalRevenue").asText())
                .compareTo(new BigDecimal("11.00")));
    }
}
