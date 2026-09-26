package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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
 * The storefront cart increments quantity on one SKU and POSTs that qty in a
 * single checkout line. Buying 3 of 5 must snapshot unitPrice, drain stock to
 * 2, and report qty 3 / $18.00. Complements qty-1 checkouts and $0.01×3
 * (PR #24).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutMultiQtyCartLineHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void checkoutQuantityThree_decrementsStockAndReportsLine() throws Exception {
        String create = "{\"name\":\"Cov30 Cart Line\",\"description\":\"d\","
                + "\"price\":6.00,\"quantity\":5,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.unitPrice").value(6.00))
                .andExpect(jsonPath("$.product.id").value((int) id))
                .andExpect(jsonPath("$.product.name").value("Cov30 Cart Line"))
                .andExpect(jsonPath("$.product.description").doesNotExist())
                .andExpect(jsonPath("$.product.image").doesNotExist());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.price").value(6.00));

        MvcResult list = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listed = null;
        for (JsonNode row : objectMapper.readTree(list.getResponse().getContentAsString())) {
            if (row.path("id").asLong() == id) {
                listed = row;
                break;
            }
        }
        Assertions.assertNotNull(listed, "checked-out SKU missing from public catalog");
        Assertions.assertEquals(2, listed.path("quantity").asInt());

        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode match = null;
        for (JsonNode row : objectMapper.readTree(report.getResponse().getContentAsString())) {
            if (row.path("productId").asLong() == id) {
                match = row;
                break;
            }
        }
        Assertions.assertNotNull(match, "multi-qty checkout missing from daily report");
        Assertions.assertEquals(3, match.path("totalQuantity").asInt());
        Assertions.assertEquals(
                0, new BigDecimal(match.path("totalRevenue").asText()).compareTo(new BigDecimal("18.00")));
    }
}
