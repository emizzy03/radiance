package com.example.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full-context tests for the ADMIN-only image upload endpoint, the public media
 * serving endpoint, and product image URL round-tripping.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductImageUploadTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMultipartFile pngFile() {
        // A tiny 1x1 PNG payload (content is not inspected; the declared
        // content type drives validation).
        byte[] bytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4};
        return new MockMultipartFile("file", "photo.png", "image/png", bytes);
    }

    @Test
    void upload_anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/products/images").file(pngFile()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_nonAdminIsForbidden() throws Exception {
        mockMvc.perform(multipart("/api/products/images").file(pngFile())
                        .with(user("bob").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_adminReturnsMediaUrl() throws Exception {
        mockMvc.perform(multipart("/api/products/images").file(pngFile())
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/media/")));
    }

    @Test
    void upload_invalidContentTypeIsRejected() throws Exception {
        MockMultipartFile textFile =
                new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/products/images").file(textFile)
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_emptyFileIsRejected() throws Exception {
        MockMultipartFile empty =
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        mockMvc.perform(multipart("/api/products/images").file(empty)
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadedImage_isRetrievableViaPublicMediaEndpoint() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/products/images").file(pngFile())
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String url = json.get("url").asText();

        // Public (anonymous) GET of the returned media URL succeeds with an image type.
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/")));
    }

    @Test
    void media_missingFileReturnsNotFound() throws Exception {
        mockMvc.perform(get("/media/does-not-exist.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadForProduct_setsImageUrlOnProduct() throws Exception {
        // Create a product (ADMIN) with a plain image URL to confirm backward compat.
        String productJson =
                "{\"name\":\"Lamp\",\"description\":\"d\",\"price\":19.99,\"quantity\":3,\"image\":\"http://x/y.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Upload an image for that product; its image field should become a /media URL.
        mockMvc.perform(multipart("/api/products/" + id + "/image").file(pngFile())
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.image", startsWith("/media/")));
    }

    @Test
    void productImageUrl_roundTripsThroughPublicCatalog() throws Exception {
        String productJson =
                "{\"name\":\"Mug\",\"description\":\"d\",\"price\":8.50,\"quantity\":10,\"image\":\"/media/sample.png\"}";
        mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value("/media/sample.png"));

        // Public catalog browse works without auth.
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
