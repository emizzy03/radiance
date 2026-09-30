package com.example.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
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
 * BMP and TIFF are not on the image allow-list (JPEG/PNG/WEBP/GIF only). A
 * rejected per-product upload must not rewrite an existing catalog image;
 * standalone BMP/TIFF POSTs are 400; a later PNG still returns {@code /media/}.
 * Complements SVG (PR #17), {@code image/jpg} (PR #29), and HTML/PDF (PR #30).
 */
@SpringBootTest
@AutoConfigureMockMvc
class BmpAndTiffUploadRejectedHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bmpAndTiffUploads_areRejected_andDoNotRewriteExistingImage() throws Exception {
        String create = "{\"name\":\"Cov34 Keep Image Sku\",\"description\":\"d\","
                + "\"price\":9.00,\"quantity\":4,\"image\":\"/media/cov34-keep.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MockMultipartFile bmp = new MockMultipartFile(
                "file", "photo.bmp", "image/bmp", new byte[]{'B', 'M', 1, 2, 3, 4});
        MockMultipartFile tiff = new MockMultipartFile(
                "file", "photo.tiff", "image/tiff", new byte[]{'I', 'I', 42, 0, 1, 2});
        byte[] pngBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4};
        MockMultipartFile png = new MockMultipartFile("file", "photo.png", "image/png", pngBytes);

        MvcResult bmpForProduct = mockMvc.perform(multipart("/api/products/" + id + "/image")
                        .file(bmp)
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode bmpError = objectMapper.readTree(bmpForProduct.getResponse().getContentAsString());
        Assertions.assertTrue(bmpError.path("message").asText().toLowerCase().contains("unsupported"),
                "BMP rejection must explain unsupported image type");

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value("/media/cov34-keep.png"));

        MvcResult tiffStandalone = mockMvc.perform(multipart("/api/products/images")
                        .file(tiff)
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode tiffError = objectMapper.readTree(tiffStandalone.getResponse().getContentAsString());
        Assertions.assertTrue(tiffError.path("message").asText().toLowerCase().contains("unsupported"),
                "TIFF rejection must explain unsupported image type");

        mockMvc.perform(multipart("/api/products/images")
                        .file(png)
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/media/")));
    }
}
