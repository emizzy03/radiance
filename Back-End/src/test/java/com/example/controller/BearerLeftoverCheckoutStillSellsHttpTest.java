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
 * A leftover {@code Authorization: Bearer} header on public checkout must not
 * block the sale. Complements malformed Basic 401 (PR #34) and Bearer 401 on
 * {@code /me} / daily-report (PR #29). Catches a JWT filter that fail-closes
 * {@code permitAll} {@code POST /api/purchases}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BearerLeftoverCheckoutStillSellsHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void leftoverBearer_doesNotBlockAnonymousCheckout_orGrantAdmin() throws Exception {
        String create = "{\"name\":\"Cov36 Bearer Sku\",\"description\":\"d\","
                + "\"price\":5.00,\"quantity\":4,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/purchases/daily-report")
                        .header("Authorization", "Bearer leftover.jwt.token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer leftover.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(5.00))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.product.id").value((int) id));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));

        JsonNode report = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode row = rowForProduct(report, id);
        Assertions.assertNotNull(row, "leftover Bearer checkout must still write a daily-report row");
        Assertions.assertEquals(1, row.path("totalQuantity").asInt());
        Assertions.assertEquals(0, new BigDecimal(row.path("totalRevenue").asText())
                .compareTo(new BigDecimal("5.00")));
    }

    private static JsonNode rowForProduct(JsonNode list, long productId) {
        for (JsonNode row : list) {
            if (row.path("productId").asLong() == productId) {
                return row;
            }
        }
        return null;
    }
}
