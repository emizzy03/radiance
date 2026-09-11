package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * HTTP checkout validation the storefront and admin "record purchase" flows
 * depend on. Complements draft coverage of missing {@code productId} / quantity 0
 * by locking in missing/negative quantity, sold-out rejection, and the 201
 * payload shape (price snapshot + product id).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutValidationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long createProduct(String name, String price, int quantity) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"description\":\"d\",\"price\":" + price
                + ",\"quantity\":" + quantity + ",\"image\":\"/media/x.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void checkout_missingQuantityIsBadRequest() throws Exception {
        long id = createProduct("QtyMissingLamp", "11.00", 4);
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkout_negativeQuantityIsBadRequest() throws Exception {
        long id = createProduct("QtyNegLamp", "11.00", 4);
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":-2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkout_soldOutProductIsBadRequestAndLeavesStockAtZero() throws Exception {
        long id = createProduct("SoldOutVase", "22.00", 0);
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0));
    }

    @Test
    void checkout_createdBodySnapshotsPriceAndProductId() throws Exception {
        long id = createProduct("SnapshotBowl", "15.50", 8);
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(15.50))
                .andExpect(jsonPath("$.product.id").value((int) id))
                .andExpect(jsonPath("$.product.name").value("SnapshotBowl"));
    }
}
