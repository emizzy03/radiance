package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
 * The storefront cart checks out lines sequentially and aborts on the first
 * failure without rolling back earlier 201s. A later insufficient-stock line
 * must not undo the earlier purchase or create a report row for the failed SKU.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StorefrontPartialCartAbortHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long createProduct(String name, int quantity) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"description\":\"d\",\"price\":6.00,\"quantity\":"
                + quantity + ",\"image\":\"/media/x.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void firstCartLineSucceeds_secondInsufficient_keepsFirstPurchaseAndSkipsFailedSku()
            throws Exception {
        long boughtId = createProduct("Cov26CartKeep", 5);
        long failedId = createProduct("Cov26CartFail", 1);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + boughtId + ",\"quantity\":2}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + failedId + ",\"quantity\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Insufficient stock")));

        mockMvc.perform(get("/api/products/" + boughtId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));
        mockMvc.perform(get("/api/products/" + failedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(1));

        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(report.getResponse().getContentAsString());
        JsonNode kept = null;
        JsonNode failed = null;
        for (JsonNode row : rows) {
            if (row.path("productId").asLong() == boughtId) {
                kept = row;
            }
            if (row.path("productId").asLong() == failedId) {
                failed = row;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(kept, "successful cart line must appear on the report");
        org.junit.jupiter.api.Assertions.assertEquals(2L, kept.get("totalQuantity").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(0, kept.get("totalRevenue").decimalValue()
                .compareTo(new java.math.BigDecimal("12.00")));
        org.junit.jupiter.api.Assertions.assertNull(failed, "failed cart line must not create a report row");
    }
}
