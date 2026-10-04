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
 * Leftover {@code Authorization: Negotiate} or {@code Token} headers on public
 * checkout must not block the sale. Complements leftover Bearer checkout
 * (PR #36) and leftover Digest checkout (PR #37). Catches an auth filter that
 * fail-closes unknown schemes on {@code permitAll} {@code POST /api/purchases}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NegotiateLeftoverCheckoutStillSellsHttpTest {

    private static final String NEGOTIATE = "Negotiate TlRMTVNTUAABAAAAB4IIogAAAAAAAAAAAAAAAAAAAAAKAKsBAQA=";
    private static final String TOKEN = "Token leftover.opaque.token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void leftoverNegotiateOrToken_doesNotBlockAnonymousCheckout_orGrantAdmin() throws Exception {
        String create = "{\"name\":\"Cov38 Negotiate Sku\",\"description\":\"d\","
                + "\"price\":7.00,\"quantity\":4,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/purchases/daily-report")
                        .header("Authorization", NEGOTIATE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/purchases/daily-report")
                        .header("Authorization", TOKEN))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", NEGOTIATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(7.00))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.product.id").value((int) id));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));

        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(7.00))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.product.id").value((int) id));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));

        JsonNode report = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode row = rowForProduct(report, id);
        Assertions.assertNotNull(row, "leftover Negotiate/Token checkout must still write a daily-report row");
        Assertions.assertEquals(2, row.path("totalQuantity").asInt());
        Assertions.assertEquals(0, new BigDecimal(row.path("totalRevenue").asText())
                .compareTo(new BigDecimal("14.00")));
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
