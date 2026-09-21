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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The admin Edit → Save path always PATCHes every catalog field from the form
 * (name, description, price, quantity, image) using HTTP Basic. Partial-field
 * tests would still pass if a full-body save dropped one of those fields.
 * Clearing the photo then saving sends {@code image:""} and must not keep the
 * previous {@code /media} URL on the public catalog.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminFullFormSavePersistsToPublicCatalogHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long createProduct(String json) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode findOnPublicList(long id) throws Exception {
        MvcResult list = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode products = objectMapper.readTree(list.getResponse().getContentAsString());
        for (JsonNode product : products) {
            if (product.path("id").asLong() == id) {
                return product;
            }
        }
        return null;
    }

    @Test
    void fullFormPatch_updatesEveryCatalogFieldOnPublicGetAndList() throws Exception {
        long id = createProduct(
                "{\"name\":\"Cov25OldLamp\",\"description\":\"old desc\","
                        + "\"price\":12.00,\"quantity\":7,\"image\":\"/media/old-lamp.png\"}");

        mockMvc.perform(patch("/api/products/" + id)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cov25NewLamp\",\"description\":\"new desc\","
                                + "\"price\":18.50,\"quantity\":4,\"image\":\"/media/new-lamp.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov25NewLamp"))
                .andExpect(jsonPath("$.description").value("new desc"))
                .andExpect(jsonPath("$.price").value(18.50))
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.image").value("/media/new-lamp.png"));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov25NewLamp"))
                .andExpect(jsonPath("$.description").value("new desc"))
                .andExpect(jsonPath("$.price").value(18.50))
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.image").value("/media/new-lamp.png"));

        JsonNode listed = findOnPublicList(id);
        org.junit.jupiter.api.Assertions.assertNotNull(listed, "edited SKU must remain on the public list");
        org.junit.jupiter.api.Assertions.assertEquals("Cov25NewLamp", listed.get("name").asText());
        org.junit.jupiter.api.Assertions.assertEquals("new desc", listed.get("description").asText());
        org.junit.jupiter.api.Assertions.assertEquals(4, listed.get("quantity").asInt());
        org.junit.jupiter.api.Assertions.assertEquals("/media/new-lamp.png", listed.get("image").asText());
    }

    @Test
    void fullFormPatch_emptyImageClearsPhotoWithoutBlankingOtherFields() throws Exception {
        long id = createProduct(
                "{\"name\":\"Cov25ClearPhotoBowl\",\"description\":\"keep desc\","
                        + "\"price\":11.00,\"quantity\":6,\"image\":\"/media/keep-bowl.png\"}");

        mockMvc.perform(patch("/api/products/" + id)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cov25ClearPhotoBowl\",\"description\":\"keep desc\","
                                + "\"price\":11.00,\"quantity\":6,\"image\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value(""))
                .andExpect(jsonPath("$.name").value("Cov25ClearPhotoBowl"))
                .andExpect(jsonPath("$.description").value("keep desc"))
                .andExpect(jsonPath("$.price").value(11.00))
                .andExpect(jsonPath("$.quantity").value(6));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value(""))
                .andExpect(jsonPath("$.name").value("Cov25ClearPhotoBowl"))
                .andExpect(jsonPath("$.quantity").value(6));

        JsonNode listed = findOnPublicList(id);
        org.junit.jupiter.api.Assertions.assertNotNull(listed, "cleared-photo SKU must remain listed");
        org.junit.jupiter.api.Assertions.assertEquals("", listed.path("image").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Cov25ClearPhotoBowl", listed.get("name").asText());
        org.junit.jupiter.api.Assertions.assertEquals(6, listed.get("quantity").asInt());
    }
}
