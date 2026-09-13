package com.example.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * HTTP media allow-list and path-safety edges that unit tests cannot prove:
 * GIF is accepted and served as {@code image/gif}, SVG is rejected (XSS),
 * client filenames never become storage paths, and traversal GETs 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MediaGifSvgAndTraversalHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void gifUpload_isServedAsGif() throws Exception {
        MockMultipartFile gif = new MockMultipartFile(
                "file", "anim.gif", "image/gif", new byte[]{'G', 'I', 'F', '8', '9', 'a', 1, 2, 3});

        MvcResult result = mockMvc.perform(multipart("/api/products/images").file(gif)
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/media/")))
                .andExpect(jsonPath("$.url", endsWith(".gif")))
                .andReturn();

        String url = objectMapper.readTree(result.getResponse().getContentAsString()).get("url").asText();
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/gif")));
    }

    @Test
    void svgUpload_isRejected() throws Exception {
        MockMultipartFile svg = new MockMultipartFile(
                "file", "icon.svg", "image/svg+xml", "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes());

        mockMvc.perform(multipart("/api/products/images").file(svg)
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_ignoresClientPathTraversalFilename() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2};
        MockMultipartFile sneaky = new MockMultipartFile(
                "file", "../../etc/passwd.png", "image/png", png);

        mockMvc.perform(multipart("/api/products/images").file(sneaky)
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/media/")))
                .andExpect(jsonPath("$.url", endsWith(".png")))
                .andExpect(jsonPath("$.url", not(org.hamcrest.Matchers.containsString("etc"))))
                .andExpect(jsonPath("$.url", not(org.hamcrest.Matchers.containsString("passwd"))))
                .andExpect(jsonPath("$.url", not(org.hamcrest.Matchers.containsString(".."))));
    }

    @Test
    void uploadForUnknownProduct_isNotFound() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2};
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", png);

        mockMvc.perform(multipart("/api/products/999999/image").file(file)
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void media_filenameContainingDotDotIsNotFound() throws Exception {
        // A single path segment that still contains ".." — the servlet will not
        // even route a bare "/media/.." (400). The storage layer must still
        // refuse names like "evil..png" so they never resolve on disk.
        mockMvc.perform(get("/media/evil..png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void uppercasePngContentType_isAccepted() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2};
        MockMultipartFile file = new MockMultipartFile("file", "photo.PNG", "IMAGE/PNG", png);

        mockMvc.perform(multipart("/api/products/images").file(file)
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/media/")))
                .andExpect(jsonPath("$.url", endsWith(".png")));
    }

    @Test
    void createProduct_thenUnknownImageTargetDoesNotChangeCatalog() throws Exception {
        // Sanity: a real product still exists after a 404 upload to another id.
        String json = "{\"name\":\"KeepImageLamp\",\"description\":\"d\",\"price\":9.99,"
                + "\"quantity\":2,\"image\":\"old.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2};
        mockMvc.perform(multipart("/api/products/999998/image")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", png))
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value("old.png"));
    }
}
