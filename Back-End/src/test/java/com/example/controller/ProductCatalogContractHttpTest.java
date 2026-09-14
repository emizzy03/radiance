package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
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
 * Storefront browse/detail reads the public catalog JSON directly. These
 * contracts keep name, description, price, quantity, and image on both the
 * list and the by-id payloads, including sold-out (qty 0) rows the UI badges.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductCatalogContractHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publicCatalog_listAndGetByIdExposeStorefrontFields() throws Exception {
        String productJson = "{\"name\":\"Cov18CatalogLamp\",\"description\":\"Brass desk lamp\","
                + "\"price\":24.50,\"quantity\":7,\"image\":\"/media/cov18-lamp.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.name").value("Cov18CatalogLamp"))
                .andExpect(jsonPath("$.description").value("Brass desk lamp"))
                .andExpect(jsonPath("$.price").value(24.50))
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.image").value("/media/cov18-lamp.png"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) id)))
                .andExpect(jsonPath("$[*].name", hasItem("Cov18CatalogLamp")))
                .andExpect(jsonPath("$[*].description", hasItem("Brass desk lamp")));
    }

    @Test
    void publicCatalog_includesSoldOutProducts() throws Exception {
        String productJson = "{\"name\":\"Cov18SoldOutMug\",\"description\":\"d\","
                + "\"price\":8.00,\"quantity\":0,\"image\":\"/media/cov18-mug.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0))
                .andExpect(jsonPath("$.name").value("Cov18SoldOutMug"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));
    }
}
