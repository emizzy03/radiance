package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Catalog create/PATCH HTTP contracts the admin UI and public storefront share:
 * invalid prices/quantities are rejected, a zero-stock listing is allowed, and
 * a partial PATCH must not blank unspecified fields.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductCatalogValidationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createProduct_zeroPriceIsRejected() throws Exception {
        String json = "{\"name\":\"FreeLamp\",\"description\":\"d\",\"price\":0,\"quantity\":3,\"image\":\"i.png\"}";
        mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_negativeQuantityIsRejected() throws Exception {
        String json = "{\"name\":\"NegQty\",\"description\":\"d\",\"price\":9.99,\"quantity\":-1,\"image\":\"i.png\"}";
        mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_zeroQuantityIsListedAsSoldOutStock() throws Exception {
        String json = "{\"name\":\"PreSoldOut\",\"description\":\"d\",\"price\":9.99,\"quantity\":0,\"image\":\"i.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("PreSoldOut"))
                .andExpect(jsonPath("$.price").value(9.99))
                .andExpect(jsonPath("$.quantity").value(0));
    }

    @Test
    void patchProduct_partialBodyKeepsUnspecifiedFields() throws Exception {
        String json = "{\"name\":\"KeepMe\",\"description\":\"original desc\",\"price\":19.99,"
                + "\"quantity\":7,\"image\":\"/media/keep.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/products/" + id).with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":29.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(29.99))
                .andExpect(jsonPath("$.name").value("KeepMe"))
                .andExpect(jsonPath("$.description").value("original desc"))
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.image").value("/media/keep.png"));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(29.99))
                .andExpect(jsonPath("$.name").value("KeepMe"))
                .andExpect(jsonPath("$.quantity").value(7));
    }
}
