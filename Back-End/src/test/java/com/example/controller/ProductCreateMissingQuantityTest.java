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
 * Catalog create must reject a missing quantity so checkout cannot later treat
 * a persisted null stock as untracked/unlimited inventory.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductCreateMissingQuantityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createProduct_missingQuantityIsRejected() throws Exception {
        String invalid = "{\"name\":\"NoQtyLamp\",\"description\":\"d\",\"price\":9.99,\"image\":\"i.png\"}";
        mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
    }
}
