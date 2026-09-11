package com.example.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS for the external React storefront/admin (localhost:3000): catalog GET
 * must reflect the allow-listed origin with credentials, and PATCH preflight
 * (admin catalog edits) must be permitted. Unlisted origins stay unreflected.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsCatalogAndPatchPreflightTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void catalogGet_allowsConfiguredOriginWithCredentials() throws Exception {
        mockMvc.perform(get("/api/products").header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void catalogGet_doesNotReflectUnlistedOrigin() throws Exception {
        // Spring's CorsFilter rejects credentialed requests from origins that are
        // not on the allow-list (403) rather than serving the body without ACAO.
        mockMvc.perform(get("/api/products").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void productPatchPreflight_allowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/products/1")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
