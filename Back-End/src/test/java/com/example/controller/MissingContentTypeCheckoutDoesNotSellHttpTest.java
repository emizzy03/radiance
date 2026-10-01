package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * A JSON checkout body with no {@code Content-Type} must not sell. Complements
 * truncated JSON (PR #26) and form-urlencoded / text/plain / multipart (PR #27).
 * Missing content type currently maps through the generic handler as 500; this
 * test does not lock that status in.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MissingContentTypeCheckoutDoesNotSellHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void checkoutWithoutContentType_doesNotDecrementStockOrAddReportRow() throws Exception {
        String create = "{\"name\":\"Cov35 NoCt Sku\",\"description\":\"d\","
                + "\"price\":6.00,\"quantity\":5,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MvcResult missingType = mockMvc.perform(post("/api/purchases")
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andReturn();
        Assertions.assertTrue(missingType.getResponse().getStatus() >= 400,
                "checkout without Content-Type must not succeed; got "
                        + missingType.getResponse().getStatus());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5));

        JsonNode report = objectMapper.readTree(
                mockMvc.perform(get("/api/purchases/daily-report")
                                .with(httpBasic("admin", "admin12345")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        for (JsonNode row : report) {
            Assertions.assertNotEquals(id, row.path("productId").asLong(),
                    "missing Content-Type checkout must not create a report row");
        }

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(6.00));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(4));
    }
}
