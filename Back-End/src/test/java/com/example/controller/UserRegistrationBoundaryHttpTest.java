package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-stack registration validation the public signup path depends on:
 * {@code @NotBlank} rejects whitespace-only identity fields, and {@code @Size(min=8)}
 * on the {@code OnCreate} group is the password floor. Complements standalone
 * {@code UserControllerTest} (no security filter) and draft tests for missing
 * username/email / blank password.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserRegistrationBoundaryHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void register_whitespaceOnlyUsernameIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"   \",\"email\":\"cov19wsuser@x.com\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void register_whitespaceOnlyEmailIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov19wsemail\",\"email\":\"   \",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void register_sevenCharacterPasswordIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov19shortpw\",\"email\":\"cov19shortpw@x.com\",\"password\":\"1234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void register_eightCharacterPasswordSucceedsAndCanAuthenticate() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov19minpw\",\"email\":\"cov19minpw@x.com\",\"password\":\"12345678\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cov19minpw"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov19minpw", "12345678")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov19minpw"));
    }
}
