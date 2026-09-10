package com.example.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Browser storefront checkout on a separate origin issues a CORS preflight
 * before {@code POST /api/purchases}. The allow-list must permit that preflight
 * from the configured local origin and must not echo an unlisted origin.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsPreflightTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void checkoutPreflight_allowsConfiguredLocalOrigin() throws Exception {
        mockMvc.perform(options("/api/purchases")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));
    }

    @Test
    void checkoutPreflight_rejectsUnlistedOrigin() throws Exception {
        mockMvc.perform(options("/api/purchases")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
