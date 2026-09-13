package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Catalog create must reject a missing or blank name so the storefront and
 * daily report never persist an unnamed SKU. Price/quantity gaps are covered
 * by other draft coverage PRs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductCreateBlankNameHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createProduct_missingNameIsRejected() throws Exception {
        String invalid = "{\"description\":\"d\",\"price\":9.99,\"quantity\":3,\"image\":\"i.png\"}";
        mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_blankNameIsRejected() throws Exception {
        String invalid = "{\"name\":\"\",\"description\":\"d\",\"price\":9.99,\"quantity\":3,\"image\":\"i.png\"}";
        mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_whitespaceNameIsRejected() throws Exception {
        String invalid = "{\"name\":\"   \",\"description\":\"d\",\"price\":9.99,\"quantity\":3,\"image\":\"i.png\"}";
        mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
    }
}
