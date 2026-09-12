package com.example.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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
 * The external storefront/admin on localhost:3000 issues CORS preflights for
 * public registration and the credentialed {@code /api/auth/me} probe. Those
 * must succeed for the allow-listed origin and must not echo an unlisted one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsSignupAndMePreflightTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registrationPreflight_allowsConfiguredLocalOrigin() throws Exception {
        mockMvc.perform(options("/api/users")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void registrationPreflight_rejectsUnlistedOrigin() throws Exception {
        mockMvc.perform(options("/api/users")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void authMePreflight_allowsAuthorizationHeader() throws Exception {
        mockMvc.perform(options("/api/auth/me")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void authMeGet_reflectsConfiguredOriginWithCredentials() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Origin", "http://localhost:3000")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
