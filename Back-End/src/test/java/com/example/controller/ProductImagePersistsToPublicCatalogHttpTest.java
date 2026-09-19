package com.example.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The storefront renders {@code product.image} from the public catalog. Admin
 * per-product upload must persist that URL on subsequent anonymous GET by id
 * and GET list — not only on the upload response itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductImagePersistsToPublicCatalogHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMultipartFile pngFile() {
        byte[] bytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4};
        return new MockMultipartFile("file", "photo.png", "image/png", bytes);
    }

    @Test
    void uploadForProduct_persistsMediaUrlOnPublicCatalogGetAndList() throws Exception {
        String productJson =
                "{\"name\":\"Cov23ImgLamp\",\"description\":\"d\",\"price\":19.99,"
                        + "\"quantity\":4,\"image\":\"placeholder.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MvcResult uploaded = mockMvc.perform(multipart("/api/products/" + id + "/image").file(pngFile())
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image", startsWith("/media/")))
                .andReturn();
        String mediaUrl = objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .get("image").asText();

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.name").value("Cov23ImgLamp"))
                .andExpect(jsonPath("$.image").value(mediaUrl));

        MvcResult list = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode products = objectMapper.readTree(list.getResponse().getContentAsString());
        JsonNode listed = null;
        for (JsonNode p : products) {
            if (p.path("id").asLong() == id) {
                listed = p;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(listed, "uploaded SKU must remain on the public list");
        org.junit.jupiter.api.Assertions.assertEquals(mediaUrl, listed.get("image").asText());

        mockMvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/")));
    }
}
