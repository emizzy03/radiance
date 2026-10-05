package com.example.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
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
 * A leftover {@code Authorization: Bearer} header on public storefront pages
 * and product images must not hide those assets. Complements leftover Bearer
 * catalog GET (PR #38). Catches a JWT filter that fail-closes {@code permitAll}
 * {@code GET /shop/**} or {@code GET /media/{file}}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BearerLeftoverShopAndMediaStillServeHttpTest {

    private static final String BEARER = "Bearer leftover.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMultipartFile pngFile() {
        byte[] bytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4};
        return new MockMultipartFile("file", "cov39-bearer.png", "image/png", bytes);
    }

    @Test
    void leftoverBearer_doesNotHideShopOrMedia_orGrantAdminWrites() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", BEARER))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/purchases/daily-report").header("Authorization", BEARER))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/shop/").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/shop/index.html"));
        mockMvc.perform(get("/shop/index.html").header("Authorization", BEARER))
                .andExpect(status().isOk());
        mockMvc.perform(get("/shop/app.js").header("Authorization", BEARER))
                .andExpect(status().isOk());
        mockMvc.perform(get("/shop/styles.css").header("Authorization", BEARER))
                .andExpect(status().isOk());

        MvcResult uploaded = mockMvc.perform(multipart("/api/products/images").file(pngFile())
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/media/")))
                .andReturn();
        String mediaUrl = objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .get("url").asText();

        mockMvc.perform(get(mediaUrl).header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/")));

        String create = "{\"name\":\"Cov39 Bearer Media Sku\",\"description\":\"keep-desc\","
                + "\"price\":3.25,\"quantity\":5,\"image\":\"" + mediaUrl + "\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode product = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = product.get("id").asLong();

        mockMvc.perform(patch("/api/products/" + id)
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cov39 Stolen\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov39 Bearer Media Sku"))
                .andExpect(jsonPath("$.image").value(mediaUrl))
                .andExpect(jsonPath("$.quantity").value(5));
    }
}
