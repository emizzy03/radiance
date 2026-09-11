package com.example.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Allow-list coverage beyond PNG (JPEG/WEBP are the common admin-upload types)
 * and the public cache headers the media endpoint advertises.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MediaJpegAndCacheTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jpegUpload_isServedAsJpegWithPublicCache() throws Exception {
        MockMultipartFile jpeg =
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3});

        MvcResult result = mockMvc.perform(multipart("/api/products/images").file(jpeg)
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/media/")))
                .andExpect(jsonPath("$.url", endsWith(".jpg")))
                .andReturn();

        String url = objectMapper.readTree(result.getResponse().getContentAsString()).get("url").asText();

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/jpeg")))
                .andExpect(header().string("Cache-Control", containsString("max-age=604800")))
                .andExpect(header().string("Cache-Control", containsString("public")));
    }

    @Test
    void webpUpload_isAcceptedAndMappedToWebpUrl() throws Exception {
        MockMultipartFile webp =
                new MockMultipartFile("file", "pic.webp", "image/webp", new byte[]{'R', 'I', 'F', 'F', 0, 1, 2, 3});

        MvcResult result = mockMvc.perform(multipart("/api/products/images").file(webp)
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", endsWith(".webp")))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        mockMvc.perform(get(json.get("url").asText()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("webp")));
    }
}
