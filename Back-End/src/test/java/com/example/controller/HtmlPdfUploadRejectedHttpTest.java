package com.example.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Public {@code /media/**} must not serve HTML or PDF uploaded under an image
 * field. The allow-list is content-type based; {@code text/html} and
 * {@code application/pdf} are 400 and must not rewrite an existing product
 * image. Complements {@code text/plain} on main, SVG reject (PR #17), and
 * {@code image/jpg} alias reject (PR #29).
 */
@SpringBootTest
@AutoConfigureMockMvc
class HtmlPdfUploadRejectedHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void htmlAndPdfUploads_areRejected_andDoNotReplaceProductImage() throws Exception {
        String create = "{\"name\":\"Cov30 Media Gate\",\"description\":\"d\","
                + "\"price\":8.00,\"quantity\":4,\"image\":\"/media/keep-me.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MockMultipartFile html = new MockMultipartFile(
                "file", "page.html", "text/html", "<script>alert(1)</script>".getBytes());
        mockMvc.perform(multipart("/api/products/images").file(html)
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        MockMultipartFile pdf = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF-1.4".getBytes());
        mockMvc.perform(multipart("/api/products/" + id + "/image").file(pdf)
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value("/media/keep-me.png"));

        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4};
        MockMultipartFile ok = new MockMultipartFile("file", "ok.png", "image/png", png);
        mockMvc.perform(multipart("/api/products/images").file(ok)
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/media/")));
    }
}
