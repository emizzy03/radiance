package com.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Signup from the separately hosted SPA is a real {@code POST /api/users}
 * with an Origin header. Preflight coverage is not enough: the browser only
 * exposes the 201 JSON (id, no password) when the actual response reflects
 * the allow-listed origin.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationCorsActualPostHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registrationPost_reflectsConfiguredOriginWithCredentials() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Origin", "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov21corsuser\",\"email\":\"cov21corsuser@x.com\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(jsonPath("$.username").value("cov21corsuser"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registrationPost_rejectsUnlistedOrigin() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov21eviluser\",\"email\":\"cov21eviluser@x.com\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
