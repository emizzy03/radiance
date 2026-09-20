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
 * HTTP Basic looks up {@code users.username}, not email. The admin login box
 * and any shopper Basic call must keep using the username; treating email as
 * an alias would collide with accounts whose username happens to be an email
 * and would silently change the auth identifier.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthEmailNotUsernameHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void basicAuth_emailIsRejected_usernameStillWorks() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov24emailuser\",\"email\":\"cov24emailuser@x.com\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov24emailuser@x.com", "password123")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov24emailuser", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov24emailuser"));
    }
}
