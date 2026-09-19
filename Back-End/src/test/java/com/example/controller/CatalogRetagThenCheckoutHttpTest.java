package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * After an admin retags a live SKU, the next public checkout must snapshot the
 * new catalog price (and the daily report must book revenue at that new
 * price). Complements the existing freeze test, which only covers purchases
 * that already happened before the PATCH.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogRetagThenCheckoutHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void patchPrice_thenCheckoutSnapshotsNewPriceAndReportUsesIt() throws Exception {
        String productJson =
                "{\"name\":\"Cov23RetagLamp\",\"description\":\"d\",\"price\":10.00,"
                        + "\"quantity\":6,\"image\":\"/media/retag.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/products/" + id).with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":25.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(25.0));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(25.0));

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(25.0))
                .andExpect(jsonPath("$.product.id").value((int) id))
                .andExpect(jsonPath("$.product.name").value("Cov23RetagLamp"));

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
        org.junit.jupiter.api.Assertions.assertNotNull(match, "daily report must include the retagged SKU");
        org.junit.jupiter.api.Assertions.assertEquals("Cov23RetagLamp", match.get("productName").asText());
        org.junit.jupiter.api.Assertions.assertEquals(2L, match.get("totalQuantity").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(
                0, new BigDecimal("50.00").compareTo(match.get("totalRevenue").decimalValue()));
    }
}
