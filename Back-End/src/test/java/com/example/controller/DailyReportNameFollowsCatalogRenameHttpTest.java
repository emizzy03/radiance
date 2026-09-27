package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Daily-report {@code productName} is joined from the live catalog row, while
 * quantity and revenue stay on the purchase snapshot. After an admin rename
 * the dashboard must show the new name on the same productId row.
 * Complements PR #28 (productId / qty / revenue after rename) which did not
 * lock the displayed name.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DailyReportNameFollowsCatalogRenameHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dailyReport_productNameFollowsAdminRename_keepsSnapshotTotals() throws Exception {
        String create = "{\"name\":\"Cov31 Old Report Name\",\"description\":\"d\","
                + "\"price\":9.25,\"quantity\":6,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(9.25));

        mockMvc.perform(patch("/api/products/" + id)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cov31 New Report Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov31 New Report Name"))
                .andExpect(jsonPath("$.price").value(9.25))
                .andExpect(jsonPath("$.quantity").value(4));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov31 New Report Name"))
                .andExpect(jsonPath("$.quantity").value(4));

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
        Assertions.assertEquals(1, matches, "rename must not split the SKU onto a second report row");
        Assertions.assertNotNull(match);
        Assertions.assertEquals("Cov31 New Report Name", match.path("productName").asText());
        Assertions.assertEquals(2, match.path("totalQuantity").asInt());
        Assertions.assertEquals(
                0, new BigDecimal(match.path("totalRevenue").asText()).compareTo(new BigDecimal("18.50")));
    }
}
