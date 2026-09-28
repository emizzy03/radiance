package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

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
 * Catalog names with quotes, apostrophes, and HTML must round-trip through
 * checkout and the admin daily report JSON. The dashboard renders
 * {@code productName} as cell text; a JPQL/JSON encoding bug would drop the
 * row or corrupt the name. Complements SQL-like username lookup (PR #26).
 */
@SpringBootTest
@AutoConfigureMockMvc
class QuotedProductNameCheckoutReportHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void checkoutAndDailyReport_preserveQuotedAndHtmlProductName() throws Exception {
        String name = "Cov32 O'Reilly \"Sale\" & <b>Co</b>";
        String description = "<script>alert(1)</script>";
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("name", name);
        product.put("description", description);
        product.put("price", new BigDecimal("4.00"));
        product.put("quantity", 7);
        product.put("image", "");

        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.description").value(description))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.description").value(description))
                .andExpect(jsonPath("$.quantity").value(7));

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(4.00))
                .andExpect(jsonPath("$.product.name").value(name));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(6))
                .andExpect(jsonPath("$.name").value(name));

        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode match = null;
        int matches = 0;
        for (JsonNode row : objectMapper.readTree(report.getResponse().getContentAsString())) {
            if (row.path("productId").asLong() == id) {
                match = row;
                matches++;
            }
        }
        Assertions.assertEquals(1, matches);
        Assertions.assertNotNull(match);
        Assertions.assertEquals(name, match.path("productName").asText());
        Assertions.assertEquals(1, match.path("totalQuantity").asInt());
        Assertions.assertEquals(0, new BigDecimal(match.path("totalRevenue").asText())
                .compareTo(new BigDecimal("4.00")));
    }
}
