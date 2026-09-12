package com.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * If {@code app.cors.allowed-origins} is blank or only separators, the allow-list
 * must fall back to the local storefront origin instead of becoming empty (which
 * would break the SPA) or accepting every origin.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=  ,  ")
@AutoConfigureMockMvc
class CorsBlankOriginsFallbackTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blankOriginsConfig_fallsBackToLocalDevOrigin() throws Exception {
        mockMvc.perform(get("/api/products").header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void blankOriginsConfig_doesNotReflectArbitraryOrigins() throws Exception {
        mockMvc.perform(get("/api/products").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
