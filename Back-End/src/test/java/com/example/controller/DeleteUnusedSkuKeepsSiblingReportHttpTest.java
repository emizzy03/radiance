package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Admin delete of an unused SKU must not erase another SKU's sales history or
 * remaining stock. Complements unused-product delete from the public list
 * (PR #26) without touching a sold sibling.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeleteUnusedSkuKeepsSiblingReportHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deleteUnusedSku_leavesSiblingStockAndDailyReportRow() throws Exception {
        long soldId = createProduct("Cov31 Keep Sold", "7.00", 5);
        long unusedId = createProduct("Cov31 Delete Unused", "2.00", 9);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + soldId + ",\"quantity\":1}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/products/" + unusedId)
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + unusedId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products/" + soldId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.name").value("Cov31 Keep Sold"));

        MvcResult list = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();
        boolean soldStillListed = false;
        boolean unusedStillListed = false;
        for (JsonNode row : objectMapper.readTree(list.getResponse().getContentAsString())) {
            if (row.path("id").asLong() == soldId) {
                soldStillListed = true;
            }
            if (row.path("id").asLong() == unusedId) {
                unusedStillListed = true;
            }
        }
        Assertions.assertTrue(soldStillListed, "sold sibling must remain on the public catalog");
        Assertions.assertFalse(unusedStillListed, "deleted unused SKU must leave the public catalog");

        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode soldRow = null;
        int soldMatches = 0;
        int unusedMatches = 0;
        for (JsonNode row : objectMapper.readTree(report.getResponse().getContentAsString())) {
            if (row.path("productId").asLong() == soldId) {
                soldRow = row;
                soldMatches++;
            }
            if (row.path("productId").asLong() == unusedId) {
                unusedMatches++;
            }
        }
        Assertions.assertEquals(1, soldMatches, "sold sibling report row must survive unused delete");
        Assertions.assertEquals(0, unusedMatches);
        Assertions.assertNotNull(soldRow);
        Assertions.assertEquals(1, soldRow.path("totalQuantity").asInt());
        Assertions.assertEquals(
                0, new BigDecimal(soldRow.path("totalRevenue").asText()).compareTo(new BigDecimal("7.00")));
    }

    private long createProduct(String name, String price, int quantity) throws Exception {
        String create = "{\"name\":\"" + name + "\",\"description\":\"d\","
                + "\"price\":" + price + ",\"quantity\":" + quantity + ",\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }
}
