package com.example.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * The admin UI uploads via {@code POST /api/products/images} and then stores
 * the returned {@code url} on create/PATCH — it does not call
 * {@code POST /api/products/{id}/image}. Storefront cards render
 * {@code product.image} from the public catalog, so that two-step contract
 * must round-trip or every new/edited photo becomes a placeholder.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminStandaloneImageThenCatalogHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMultipartFile pngFile() {
        byte[] bytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4};
        return new MockMultipartFile("file", "photo.png", "image/png", bytes);
    }

    private String uploadMediaUrl() throws Exception {
        MvcResult uploaded = mockMvc.perform(multipart("/api/products/images").file(pngFile())
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/media/")))
                .andReturn();
        return objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("url").asText();
    }

    @Test
    void standaloneUpload_thenCreateAndPatch_persistUrlOnPublicCatalog() throws Exception {
        String createUrl = uploadMediaUrl();

        String productJson = "{\"name\":\"Cov24ImgCreate\",\"description\":\"d\",\"price\":19.99,"
                + "\"quantity\":4,\"image\":\"" + createUrl + "\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value(createUrl))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value(createUrl));

        MvcResult list = mockMvc.perform(get("/api/products")).andExpect(status().isOk()).andReturn();
        JsonNode listed = findProduct(objectMapper.readTree(list.getResponse().getContentAsString()), id);
        org.junit.jupiter.api.Assertions.assertNotNull(listed, "created SKU must remain on the public list");
        org.junit.jupiter.api.Assertions.assertEquals(createUrl, listed.get("image").asText());

        mockMvc.perform(get(createUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/")));

        String replaceUrl = uploadMediaUrl();
        org.junit.jupiter.api.Assertions.assertNotEquals(createUrl, replaceUrl);

        mockMvc.perform(patch("/api/products/" + id).with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"" + replaceUrl + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value(replaceUrl))
                .andExpect(jsonPath("$.name").value("Cov24ImgCreate"))
                .andExpect(jsonPath("$.price").value(19.99))
                .andExpect(jsonPath("$.quantity").value(4));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value(replaceUrl))
                .andExpect(jsonPath("$.name").value("Cov24ImgCreate"));

        mockMvc.perform(get(replaceUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/")));
    }

    private static JsonNode findProduct(JsonNode products, long id) {
        for (JsonNode p : products) {
            if (p.path("id").asLong() == id) {
                return p;
            }
        }
        return null;
    }
}
