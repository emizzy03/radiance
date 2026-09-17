package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
 * An empty JSON PATCH must be a no-op. If update started copying nulls from
 * the body onto the entity, a {@code {}} save would blank name/price/image
 * and take the SKU off the storefront.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductEmptyPatchHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void patchProduct_emptyBodyDoesNotBlankCatalogFields() throws Exception {
        String json = "{\"name\":\"Cov21KeepAllLamp\",\"description\":\"keep this desc\","
                + "\"price\":21.50,\"quantity\":9,\"image\":\"/media/keep-all.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/products/" + id).with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov21KeepAllLamp"))
                .andExpect(jsonPath("$.description").value("keep this desc"))
                .andExpect(jsonPath("$.price").value(21.50))
                .andExpect(jsonPath("$.quantity").value(9))
                .andExpect(jsonPath("$.image").value("/media/keep-all.png"));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov21KeepAllLamp"))
                .andExpect(jsonPath("$.description").value("keep this desc"))
                .andExpect(jsonPath("$.price").value(21.50))
                .andExpect(jsonPath("$.quantity").value(9))
                .andExpect(jsonPath("$.image").value("/media/keep-all.png"));
    }
}
