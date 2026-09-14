package com.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS allow-list is GET/POST/PATCH/DELETE/OPTIONS only. A browser PUT
 * preflight from the storefront origin must not be accepted, or a future PUT
 * mapping would be reachable cross-origin without an explicit review.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsDisallowedMethodTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void putPreflight_fromAllowedOrigin_isForbidden() throws Exception {
        mockMvc.perform(options("/api/products/1")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isForbidden());
    }
}
