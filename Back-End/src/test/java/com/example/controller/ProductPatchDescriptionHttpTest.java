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
 * Storefront search and the product-detail pane read {@code description}.
 * A description-only PATCH (and an image-only PATCH) must persist those fields
 * without blanking name/price/quantity. Complements draft price-only PATCH
 * coverage.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductPatchDescriptionHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long createProduct() throws Exception {
        String json = "{\"name\":\"Cov19PatchLamp\",\"description\":\"old copy\","
                + "\"price\":18.00,\"quantity\":4,\"image\":\"/media/old.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void patchDescriptionOnly_persistsOnPublicGetAndKeepsOtherFields() throws Exception {
        long id = createProduct();

        mockMvc.perform(patch("/api/products/" + id).with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"lavender soy candle\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("lavender soy candle"));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov19PatchLamp"))
                .andExpect(jsonPath("$.description").value("lavender soy candle"))
                .andExpect(jsonPath("$.price").value(18.00))
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.image").value("/media/old.png"));
    }

    @Test
    void patchImageOnly_persistsOnPublicGetAndKeepsDescription() throws Exception {
        long id = createProduct();

        mockMvc.perform(patch("/api/products/" + id).with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"/media/new.png\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value("/media/new.png"))
                .andExpect(jsonPath("$.description").value("old copy"))
                .andExpect(jsonPath("$.name").value("Cov19PatchLamp"))
                .andExpect(jsonPath("$.quantity").value(4));
    }
}
