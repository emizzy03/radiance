package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Admin upload UI reads {@code err.message} on failure and stores {@code url}
 * on success. Complements the existing happy-path / invalid-type tests by
 * locking the JSON error shape, PNG Content-Type, and UUID uniqueness
 * (two uploads must not overwrite each other).
 */
@SpringBootTest
@AutoConfigureMockMvc
class MediaUploadContractHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMultipartFile pngFile() {
        byte[] bytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4};
        return new MockMultipartFile("file", "photo.png", "image/png", bytes);
    }

    @Test
    void upload_pngIsServedAsImagePng() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/products/images").file(pngFile())
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/media/")))
                .andReturn();

        String url = objectMapper.readTree(result.getResponse().getContentAsString()).get("url").asText();
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void upload_invalidTypeReturnsJsonMessageForAdminUi() throws Exception {
        MockMultipartFile html = new MockMultipartFile(
                "file", "page.html", "text/html", "<html></html>".getBytes());
        mockMvc.perform(multipart("/api/products/images").file(html)
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unsupported")));
    }

    @Test
    void upload_twoFilesGetDistinctUrlsAndBothRemainReadable() throws Exception {
        MvcResult first = mockMvc.perform(multipart("/api/products/images").file(pngFile())
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(multipart("/api/products/images").file(pngFile())
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode a = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode b = objectMapper.readTree(second.getResponse().getContentAsString());
        String urlA = a.get("url").asText();
        String urlB = b.get("url").asText();

        assertThat(urlA).isNotEqualTo(urlB);
        assertThat(urlA).startsWith("/media/");
        assertThat(urlB).startsWith("/media/");

        mockMvc.perform(get(urlA)).andExpect(status().isOk());
        mockMvc.perform(get(urlB)).andExpect(status().isOk());
    }
}
