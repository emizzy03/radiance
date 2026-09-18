package com.example.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The separately hosted admin on localhost:3000 sends {@code Authorization} on
 * daily-report GET, catalog POST/PATCH, and image upload. Preflight must allow
 * that header, and the actual responses must reflect the origin — otherwise
 * the browser hides report rows, create/edit JSON, and the uploaded
 * {@code url} the form stores on save.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsAdminDashboardHttpTest {

    private static final String ORIGIN = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long createProduct(String name) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"description\":\"d\",\"price\":13.00,"
                + "\"quantity\":5,\"image\":\"/media/c.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void dailyReportPreflight_allowsAuthorization() throws Exception {
        mockMvc.perform(options("/api/purchases/daily-report")
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void dailyReportGet_reflectsConfiguredOriginWithCredentials() throws Exception {
        mockMvc.perform(get("/api/purchases/daily-report")
                        .header("Origin", ORIGIN)
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void createProductPost_reflectsConfiguredOrigin() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Origin", ORIGIN)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cov22CorsCreateBowl\",\"description\":\"d\","
                                + "\"price\":8.00,\"quantity\":3,\"image\":\"/media/bowl.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov22CorsCreateBowl"))
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void patchProduct_reflectsConfiguredOrigin() throws Exception {
        long id = createProduct("Cov22CorsPatchLamp");

        mockMvc.perform(patch("/api/products/" + id)
                        .header("Origin", ORIGIN)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cov22CorsPatchedLamp\",\"description\":\"d\","
                                + "\"price\":14.00,\"quantity\":5,\"image\":\"/media/c.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov22CorsPatchedLamp"))
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void imageUploadPreflight_allowsAuthorization() throws Exception {
        mockMvc.perform(options("/api/products/images")
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void imageUploadPost_reflectsConfiguredOriginAndReturnsUrl() throws Exception {
        MockMultipartFile png = new MockMultipartFile(
                "file", "photo.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3});

        mockMvc.perform(multipart("/api/products/images").file(png)
                        .header("Origin", ORIGIN)
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
