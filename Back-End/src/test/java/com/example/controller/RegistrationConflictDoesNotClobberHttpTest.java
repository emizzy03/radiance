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
 * Duplicate username/email registration is 409. That status alone is not
 * enough: a naive upsert-on-conflict would still return 409 after overwriting
 * the victim's password or inserting a second account. Original credentials
 * must keep working and the attacker password must not.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationConflictDoesNotClobberHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void duplicateUsername_doesNotChangeOriginalPassword() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov24orig\",\"email\":\"cov24orig@x.com\","
                                + "\"password\":\"origpass123\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov24orig\",\"email\":\"cov24attacker@x.com\","
                                + "\"password\":\"attacker999\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov24orig", "origpass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov24orig"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov24orig", "attacker999")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmail_doesNotCreateAttackerUsername() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov24mailowner\",\"email\":\"cov24shared@x.com\","
                                + "\"password\":\"ownerpass1\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov24mailthief\",\"email\":\"cov24shared@x.com\","
                                + "\"password\":\"thiefpass1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov24mailowner", "ownerpass1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov24mailowner"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov24mailthief", "thiefpass1")))
                .andExpect(status().isUnauthorized());
    }
}
