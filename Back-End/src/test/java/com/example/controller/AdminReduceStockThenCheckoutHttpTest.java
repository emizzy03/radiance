package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Inventory correction is a downward PATCH of {@code quantity}. Checkout must
 * honor the new cap: an order larger than the corrected stock is 400 and must
 * not decrement, and buying the remaining units must drain to 0. Complements
 * restock-after-sold-out coverage (the opposite direction).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReduceStockThenCheckoutHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reducingStock_capsSubsequentCheckoutAndLeavesRemainderUnchangedOnOversizedOrder() throws Exception {
        String createJson = "{\"name\":\"Cov25ReduceStockMug\",\"description\":\"d\","
                + "\"price\":9.00,\"quantity\":10,\"image\":\"/media/mug.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/products/" + id)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(9.00));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0));
    }
}
