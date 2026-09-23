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
 * {@code decrementStock} subtracts the requested qty in SQL. Existing
 * insufficient-stock tests use small numbers (buy 5 when 2 remain). A
 * {@code Integer.MAX_VALUE} order against tiny stock must not wrap the
 * column negative, and a successful decrement from {@code MAX_VALUE} stock
 * must leave {@code MAX_VALUE - 1}, not overflow to a negative quantity.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutMaxIntQuantityDoesNotWrapStockHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long createProduct(String name, int quantity) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"description\":\"d\",\"price\":4.00,\"quantity\":"
                + quantity + ",\"image\":\"/media/x.png\"}";
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
    void checkout_maxIntQuantityAgainstSmallStock_isRejectedAndLeavesStockUnchanged()
            throws Exception {
        long id = createProduct("Cov27MaxBuy", 5);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":" + Integer.MAX_VALUE + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Insufficient stock")));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5));

        org.junit.jupiter.api.Assertions.assertNull(reportRowFor(id),
                "rejected MAX_VALUE checkout must not create a report row");
    }

    @Test
    void checkout_oneUnitFromMaxIntStock_decrementsWithoutWrapping() throws Exception {
        long id = createProduct("Cov27MaxStock", Integer.MAX_VALUE);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.unitPrice").value(4.00));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(Integer.MAX_VALUE - 1));

        JsonNode row = reportRowFor(id);
        org.junit.jupiter.api.Assertions.assertNotNull(row, "successful max-stock checkout must appear on the report");
        org.junit.jupiter.api.Assertions.assertEquals(1L, row.get("totalQuantity").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(0, row.get("totalRevenue").decimalValue()
                .compareTo(new java.math.BigDecimal("4.00")));
    }
}
