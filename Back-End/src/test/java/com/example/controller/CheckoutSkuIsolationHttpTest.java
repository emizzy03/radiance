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
 * The storefront cart checks out each line as its own {@code POST /api/purchases}.
 * A buy of SKU A must never decrement SKU B, and a sequential two-line cart
 * must decrement each SKU by only its own quantity.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutSkuIsolationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long createProduct(String name, int quantity) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"description\":\"d\",\"price\":12.00,\"quantity\":"
                + quantity + ",\"image\":\"/media/x.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void checkout_oneSkuDoesNotDecrementSiblingStock() throws Exception {
        long lampId = createProduct("IsoLampCov20", 10);
        long mugId = createProduct("IsoMugCov20", 7);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + lampId + ",\"quantity\":3}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products/" + lampId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(7));
        mockMvc.perform(get("/api/products/" + mugId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(7));
    }

    @Test
    void checkout_sequentialCartLinesDecrementEachSkuIndependently() throws Exception {
        long candleId = createProduct("IsoCandleCov20", 5);
        long vaseId = createProduct("IsoVaseCov20", 4);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + candleId + ",\"quantity\":2}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + vaseId + ",\"quantity\":1}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products/" + candleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));
        mockMvc.perform(get("/api/products/" + vaseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));
    }

    @Test
    void checkout_oversizedOrderOnOneSkuLeavesSiblingStockUnchanged() throws Exception {
        long bowlId = createProduct("IsoBowlCov20", 2);
        long trayId = createProduct("IsoTrayCov20", 9);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + bowlId + ",\"quantity\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient stock")));

        mockMvc.perform(get("/api/products/" + bowlId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));
        mockMvc.perform(get("/api/products/" + trayId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(9));
    }
}
