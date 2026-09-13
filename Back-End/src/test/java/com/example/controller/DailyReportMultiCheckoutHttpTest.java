package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Admin daily-report HTTP must SUM multiple public checkouts of the same
 * product (and keep a second SKU on its own row). Complements the JPA
 * aggregation test and the single-purchase price-snapshot HTTP test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DailyReportMultiCheckoutHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long createProduct(String name, String price, int quantity) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"description\":\"d\",\"price\":" + price
                + ",\"quantity\":" + quantity + ",\"image\":\"/media/x.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode reportRowFor(long productId) throws Exception {
        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(report.getResponse().getContentAsString());
        for (JsonNode row : rows) {
            if (row.path("productId").asLong() == productId) {
                return row;
            }
        }
        return null;
    }

    @Test
    void dailyReport_sumsTwoCheckoutsOfSameProductAndKeepsSecondSkuSeparate() throws Exception {
        long lampId = createProduct("AggLampCov17", "10.00", 20);
        long mugId = createProduct("AggMugCov17", "5.00", 20);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + lampId + ",\"quantity\":2}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + lampId + ",\"quantity\":3}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + mugId + ",\"quantity\":4}"))
                .andExpect(status().isCreated());

        JsonNode lamp = reportRowFor(lampId);
        JsonNode mug = reportRowFor(mugId);
        org.junit.jupiter.api.Assertions.assertNotNull(lamp, "daily report must include lamp checkouts");
        org.junit.jupiter.api.Assertions.assertNotNull(mug, "daily report must include mug checkout");

        org.junit.jupiter.api.Assertions.assertEquals("AggLampCov17", lamp.get("productName").asText());
        org.junit.jupiter.api.Assertions.assertEquals(5L, lamp.get("totalQuantity").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(
                0, new BigDecimal("50.00").compareTo(lamp.get("totalRevenue").decimalValue()));

        org.junit.jupiter.api.Assertions.assertEquals("AggMugCov17", mug.get("productName").asText());
        org.junit.jupiter.api.Assertions.assertEquals(4L, mug.get("totalQuantity").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(
                0, new BigDecimal("20.00").compareTo(mug.get("totalRevenue").decimalValue()));
    }
}
