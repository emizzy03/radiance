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
 * After a successful partial buy, a later line that exceeds remaining stock
 * must 400 without touching that remainder; buying the exact remainder then
 * drains to 0 and SUMs both sales on one report row. Complements last-unit
 * double-submit (PR #33), two-SKU partial cart abort (PR #26), and a single
 * sufficient multi-qty line (PR #30).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PartialBuyRemainderCheckoutHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void partialBuy_thenOversizedRemainder_thenExactDrain_doesNotOversell() throws Exception {
        String create = "{\"name\":\"Cov34 Remainder Sku\",\"description\":\"d\","
                + "\"price\":4.00,\"quantity\":5,\"image\":\"\"}";
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
                .andExpect(jsonPath("$.unitPrice").value(4.00));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));

        MvcResult oversized = mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":3}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode error = objectMapper.readTree(oversized.getResponse().getContentAsString());
        Assertions.assertTrue(error.path("message").asText().toLowerCase().contains("insufficient"),
                "buy that exceeds remaining stock must explain insufficient stock");

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));

        JsonNode reportAfterFail = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode afterFail = rowForProduct(reportAfterFail, id);
        Assertions.assertNotNull(afterFail);
        Assertions.assertEquals(3, afterFail.path("totalQuantity").asInt());
        Assertions.assertEquals(0, new BigDecimal(afterFail.path("totalRevenue").asText())
                .compareTo(new BigDecimal("12.00")));

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0));

        JsonNode reportAfterDrain = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        int matches = 0;
        JsonNode drained = null;
        for (JsonNode candidate : reportAfterDrain) {
            if (candidate.path("productId").asLong() == id) {
                drained = candidate;
                matches++;
            }
        }
        Assertions.assertEquals(1, matches, "both sales of the same SKU must stay on one report row");
        Assertions.assertNotNull(drained);
        Assertions.assertEquals(5, drained.path("totalQuantity").asInt());
        Assertions.assertEquals(0, new BigDecimal(drained.path("totalRevenue").asText())
                .compareTo(new BigDecimal("20.00")));
        Assertions.assertEquals("Cov34 Remainder Sku", drained.path("productName").asText());
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
