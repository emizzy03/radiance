package com.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Production CORS is a comma-separated allow-list (Railway API + Vercel
 * storefront, plus local dev). Parsing must trim entries so a space after the
 * comma does not silently drop the deployed origin, and unlisted origins must
 * still be rejected. Complements draft tests for wildcard rejection, blank
 * fallback, and single-origin localhost:3000.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000, https://shop.example.com")
@AutoConfigureMockMvc
class CorsMultiOriginAllowListTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void catalogGet_fromTrimmedSecondOrigin_reflectsOriginAndCredentials() throws Exception {
        mockMvc.perform(get("/api/products").header(HttpHeaders.ORIGIN, "https://shop.example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://shop.example.com"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void catalogGet_fromFirstOrigin_stillAllowed() throws Exception {
        mockMvc.perform(get("/api/products").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void catalogGet_fromUnlistedOrigin_isForbidden() throws Exception {
        mockMvc.perform(get("/api/products").header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void checkoutPreflight_fromSecondOrigin_isAllowed() throws Exception {
        mockMvc.perform(options("/api/purchases")
                        .header(HttpHeaders.ORIGIN, "https://shop.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://shop.example.com"));
    }
}
