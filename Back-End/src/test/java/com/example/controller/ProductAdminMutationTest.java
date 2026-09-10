package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Admin catalog mutations (PATCH/DELETE and per-product image upload) must stay
 * ADMIN-only, 404 on missing products, and actually persist for the public catalog.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductAdminMutationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long createProduct(String name) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"description\":\"d\",\"price\":12.50,\"quantity\":8,\"image\":\"old.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private MockMultipartFile pngFile() {
        byte[] bytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4};
        return new MockMultipartFile("file", "photo.png", "image/png", bytes);
    }

    @Test
    void patchProduct_anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":1.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteProduct_anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchProduct_adminUpdatesPublicCatalog() throws Exception {
        long id = createProduct("PatchableLamp");

        mockMvc.perform(patch("/api/products/" + id).with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":18.00,\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(18.0))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.name").value("PatchableLamp"))
                .andExpect(jsonPath("$.image").value("old.png"));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(18.0))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void patchProduct_unknownIdIsNotFound() throws Exception {
        mockMvc.perform(patch("/api/products/999999").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":1.00}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProduct_unusedProductIsRemovedFromCatalog() throws Exception {
        long id = createProduct("DisposableCandle");

        mockMvc.perform(delete("/api/products/" + id).with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProduct_unknownIdIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/products/999999").with(user("boss").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadForProduct_anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/products/1/image").file(pngFile()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadForProduct_nonAdminIsForbidden() throws Exception {
        mockMvc.perform(multipart("/api/products/1/image").file(pngFile())
                        .with(user("bob").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadForProduct_invalidTypeIsRejected() throws Exception {
        long id = createProduct("NeedsImage");
        MockMultipartFile textFile =
                new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/products/" + id + "/image").file(textFile)
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value("old.png"));
    }

    @Test
    void createProduct_missingPriceIsRejected() throws Exception {
        String invalid = "{\"name\":\"NoPrice\",\"description\":\"d\",\"quantity\":5,\"image\":\"i.png\"}";
        mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
    }
}
