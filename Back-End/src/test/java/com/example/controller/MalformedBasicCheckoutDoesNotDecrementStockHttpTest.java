package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
 * Public checkout is {@code permitAll}, but a syntactically invalid
 * {@code Authorization: Basic} header must still fail closed. Complements
 * wrong-password / unknown-user Basic on checkout (PR #33), which still send
 * well-formed {@code user:pass} tokens. Catches treating a Basic parse failure
 * as anonymous success on {@code POST /api/purchases}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MalformedBasicCheckoutDoesNotDecrementStockHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void malformedBasic_onPublicCheckout_doesNotDecrementStockOrAddReportRow() throws Exception {
        String create = "{\"name\":\"Cov34 Malformed Basic Sku\",\"description\":\"d\","
                + "\"price\":6.00,\"quantity\":3,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String checkout = "{\"productId\":" + id + ",\"quantity\":1}";

        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Basic not-valid-base64!!!")
                        .contentType(MediaType.APPLICATION_JSON).content(checkout))
                .andExpect(status().isUnauthorized());

        String noColon = Base64.getEncoder()
                .encodeToString("nocolonhere".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Basic " + noColon)
                        .contentType(MediaType.APPLICATION_JSON).content(checkout))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));

        JsonNode reportAfterFailed = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        Assertions.assertNull(rowForProduct(reportAfterFailed, id),
                "malformed Basic must not insert a daily-report row");

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON).content(checkout))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(6.00));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));

        JsonNode reportAfterAnonymous = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode row = rowForProduct(reportAfterAnonymous, id);
        Assertions.assertNotNull(row, "anonymous checkout after malformed Basic must still record the sale");
        Assertions.assertEquals(1, row.path("totalQuantity").asInt());
        Assertions.assertEquals(0, new BigDecimal(row.path("totalRevenue").asText())
                .compareTo(new BigDecimal("6.00")));
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
