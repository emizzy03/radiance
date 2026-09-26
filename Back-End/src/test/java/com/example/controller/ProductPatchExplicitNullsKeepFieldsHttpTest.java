package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Explicit JSON nulls on PATCH must not blank catalog fields (same PATCH
 * semantics as omitting keys). Complements empty {@code {}} PATCH (PR #21)
 * and description/image-only PATCH (PR #19).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductPatchExplicitNullsKeepFieldsHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void productPatchExplicitNulls_doesNotBlankCatalog() throws Exception {
        String create = "{\"name\":\"Cov30 Keep Fields\",\"description\":\"keep-desc\","
                + "\"price\":12.50,\"quantity\":8,\"image\":\"/media/keep.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/products/" + id)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":null,\"description\":null,\"price\":null,"
                                + "\"quantity\":null,\"image\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov30 Keep Fields"))
                .andExpect(jsonPath("$.description").value("keep-desc"))
                .andExpect(jsonPath("$.price").value(12.50))
                .andExpect(jsonPath("$.quantity").value(8))
                .andExpect(jsonPath("$.image").value("/media/keep.png"));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov30 Keep Fields"))
                .andExpect(jsonPath("$.description").value("keep-desc"))
                .andExpect(jsonPath("$.price").value(12.50))
                .andExpect(jsonPath("$.quantity").value(8))
                .andExpect(jsonPath("$.image").value("/media/keep.png"));
    }
}
