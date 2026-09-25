package com.example.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The upload allow-list keys on the declared content type, not the filename.
 * {@code image/jpeg} is accepted; the non-standard {@code image/jpg} alias
 * used by some browsers must not bypass the list. Complements SVG reject
 * and {@code IMAGE/PNG} case-fold (PR #17).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ImageJpgAliasRejectedHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void imageJpgContentType_isRejected_imageJpegStillWorks() throws Exception {
        byte[] bytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02, 0x03};
        MockMultipartFile jpgAlias =
                new MockMultipartFile("file", "photo.jpg", "image/jpg", bytes);
        mockMvc.perform(multipart("/api/products/images").file(jpgAlias)
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        MockMultipartFile jpeg =
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes);
        mockMvc.perform(multipart("/api/products/images").file(jpeg)
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/media/")));
    }
}
