package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Admin login calls {@code GET /api/auth/me} with Basic credentials. A missing
 * username must be 401 (same as a wrong password) so the UI can treat both as
 * invalid credentials. Complements {@code SecurityAdminEndpointsTest}'s wrong
 * password case and draft {@code /me} 401-vs-admin tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthUnknownUserHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void me_unknownUsernameIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(httpBasic("no-such-user-cov19", "password123")))
                .andExpect(status().isUnauthorized());
    }
}
