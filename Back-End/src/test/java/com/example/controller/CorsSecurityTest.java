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
 * CORS hardening: allowed origins are an explicit list, credentials are on, and
 * a configured wildcard must never be reflected (browsers forbid credentials +
 * {@code *} and it would otherwise let any site read authenticated responses).
 */
@SpringBootTest(properties = "app.cors.allowed-origins=*")
@AutoConfigureMockMvc
class CorsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void wildcardConfig_doesNotReflectArbitraryOrigins() throws Exception {
        // Spring's CORS filter rejects disallowed origins with 403 and must not
        // echo them back in Access-Control-Allow-Origin.
        mockMvc.perform(get("/api/products").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void wildcardConfig_fallsBackToLocalDevOrigin() throws Exception {
        mockMvc.perform(get("/api/products").header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }
}
