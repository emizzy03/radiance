package com.example.controller;

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
 * Truncated JSON, an empty body, or a JSON array must not record a purchase or
 * decrement stock. The exact 4xx/5xx mapping of unreadble bodies is not locked
 * in; the invariant is that checkout is not a success.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutMalformedBodyDoesNotSellHttpTest {

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

    @Test
    void truncatedJson_doesNotDecrementStock() throws Exception {
        long id = createProduct("Cov26BadJsonLamp", 6);

        MvcResult result = mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2"))
                .andReturn();
        int status = result.getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertFalse(status == 200 || status == 201,
                "truncated JSON must not check out; got " + status);

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(6));
    }

    @Test
    void jsonArrayBody_doesNotDecrementStock() throws Exception {
        long id = createProduct("Cov26ArrayBodyMug", 4);

        MvcResult result = mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"productId\":" + id + ",\"quantity\":1}]"))
                .andReturn();
        int status = result.getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertFalse(status == 200 || status == 201,
                "array body must not check out; got " + status);

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(4));
    }
}
