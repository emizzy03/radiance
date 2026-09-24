package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Unknown-SKU checkout of {@code 999999} is already covered. {@code 0} and
 * {@code -1} are valid JSON numbers that some binders treat as "missing".
 * They must be rejected, must not decrement any catalog row, and must not
 * add a daily-report line.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutZeroAndNegativeProductIdHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void checkout_zeroAndNegativeProductIdDoNotSell() throws Exception {
        String create = "{\"name\":\"Cov28 Sentinel\",\"description\":\"d\","
                + "\"price\":4.00,\"quantity\":5,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        for (int productId : new int[] {0, -1}) {
            mockMvc.perform(post("/api/purchases")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":" + productId + ",\"quantity\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").exists());
        }

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5));

        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(report.getResponse().getContentAsString());
        for (JsonNode row : rows) {
            org.junit.jupiter.api.Assertions.assertNotEquals(
                    id, row.path("productId").asLong(),
                    "invalid productId checkout must not create a report row");
        }
    }
}
