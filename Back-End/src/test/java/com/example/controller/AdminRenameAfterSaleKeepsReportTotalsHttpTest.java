package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * The admin Edit→Save flow can rename a SKU after customers have already
 * checked out. Daily-report revenue is grouped by live product id and name;
 * a rename must not drop the row or change snapshot quantity/revenue, and
 * the public catalog must show the new name with the already-decremented
 * stock.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminRenameAfterSaleKeepsReportTotalsHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminRenameAfterCheckout_reportKeepsProductIdQtyAndRevenue() throws Exception {
        String create = "{\"name\":\"Cov28 Rename Before\",\"description\":\"d\","
                + "\"price\":5.00,\"quantity\":8,\"image\":\"\"}";
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
                .andExpect(jsonPath("$.unitPrice").value(5.00))
                .andExpect(jsonPath("$.quantity").value(2));

        mockMvc.perform(patch("/api/products/" + id)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cov28 Rename After\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov28 Rename After"))
                .andExpect(jsonPath("$.price").value(5.00))
                .andExpect(jsonPath("$.quantity").value(6));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov28 Rename After"))
                .andExpect(jsonPath("$.quantity").value(6));

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
        org.junit.jupiter.api.Assertions.assertNotNull(match, "daily report lost the SKU after rename");
        org.junit.jupiter.api.Assertions.assertEquals(2, match.path("totalQuantity").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                0, new java.math.BigDecimal(match.path("totalRevenue").asText()).compareTo(
                        new java.math.BigDecimal("10.00")));
    }
}
