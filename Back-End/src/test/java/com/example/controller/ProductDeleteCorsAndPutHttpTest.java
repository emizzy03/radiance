package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Admin catalog deletes from localhost:3000 need a DELETE CORS preflight, and
 * product updates are PATCH-only so a PUT cannot full-replace / blank fields.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductDeleteCorsAndPutHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void productDeletePreflight_allowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/products/1")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "DELETE")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("DELETE")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void productDeletePreflight_rejectsUnlistedOrigin() throws Exception {
        mockMvc.perform(options("/api/products/1")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "DELETE")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void putProduct_isNotAllowedAndDoesNotBlankExistingFields() throws Exception {
        String json = "{\"name\":\"PutGuardLamp20\",\"description\":\"keep-me\",\"price\":19.99,"
                + "\"quantity\":6,\"image\":\"keep.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // PUT is not mapped (updates are PATCH-only). Do not lock a specific
        // 405 vs generic-handler 500; the catalog must remain unchanged.
        MvcResult putResult = mockMvc.perform(put("/api/products/" + id).with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"wiped\"}"))
                .andReturn();
        assertThat(putResult.getResponse().getStatus()).isGreaterThanOrEqualTo(400);

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("PutGuardLamp20"))
                .andExpect(jsonPath("$.description").value("keep-me"))
                .andExpect(jsonPath("$.price").value(19.99))
                .andExpect(jsonPath("$.quantity").value(6))
                .andExpect(jsonPath("$.image").value("keep.png"));
    }
}
