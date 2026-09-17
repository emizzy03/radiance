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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The admin dashboard "Record purchase" button posts {@code /api/purchases}
 * with HTTP Basic (not anonymously). Checkout is {@code permitAll}, so a
 * credentialed admin must still get 201 and stock must decrement — a matcher
 * reorder that required anonymous-only or blocked authenticated POSTs would
 * break the admin UI while leaving the public storefront green.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminBasicCheckoutHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void bootstrappedAdmin_canRecordPurchaseWithBasicAuth() throws Exception {
        String productJson =
                "{\"name\":\"Cov21AdminBuyLamp\",\"description\":\"d\",\"price\":15.00,\"quantity\":8,\"image\":\"/media/a.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/purchases").with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.unitPrice").value(15.0))
                .andExpect(jsonPath("$.product.id").value((int) id));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5));
    }
}
