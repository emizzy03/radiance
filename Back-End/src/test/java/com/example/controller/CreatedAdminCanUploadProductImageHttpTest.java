package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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
 * A created second admin must be able to run the dashboard photo flows
 * (standalone {@code POST /api/products/images} and per-product
 * {@code POST /{id}/image}). Complements seeded-admin uploads on main / PR #24
 * and created-admin POST product (PR #31). Catches hard-coding the bootstrap
 * username on the image endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreatedAdminCanUploadProductImageHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static MockMultipartFile pngFile(String originalFilename) {
        byte[] bytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4};
        return new MockMultipartFile("file", originalFilename, "image/png", bytes);
    }

    @Test
    void createdAdmin_canUploadStandaloneAndPerProductImages() throws Exception {
        mockMvc.perform(post("/api/users/admin/cov35imgadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov35imgadmin@x.com\",\"password\":\"imgadmin99\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov35imgadmin", "imgadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        MvcResult standalone = mockMvc.perform(multipart("/api/products/images")
                        .file(pngFile("cov35-standalone.png"))
                        .with(httpBasic("cov35imgadmin", "imgadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/media/")))
                .andReturn();
        String standaloneUrl = objectMapper.readTree(standalone.getResponse().getContentAsString())
                .get("url").asText();

        mockMvc.perform(get(standaloneUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/")));

        String create = "{\"name\":\"Cov35 Image Sku\",\"description\":\"d\","
                + "\"price\":3.00,\"quantity\":2,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("cov35imgadmin", "imgadmin99"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(multipart("/api/products/" + id + "/image")
                        .file(pngFile("cov35-product.png"))
                        .with(httpBasic("cov35imgadmin", "imgadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.image", startsWith("/media/")));

        MvcResult publicGet = mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image", startsWith("/media/")))
                .andReturn();
        String productUrl = objectMapper.readTree(publicGet.getResponse().getContentAsString())
                .get("image").asText();

        mockMvc.perform(get(productUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/")));

        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/products"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode listed = null;
        for (JsonNode row : list) {
            if (row.path("id").asLong() == id) {
                listed = row;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(listed);
        org.junit.jupiter.api.Assertions.assertTrue(listed.path("image").asText().startsWith("/media/"));
    }
}
