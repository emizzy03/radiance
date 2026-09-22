package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
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
 * Public registration must not hijack the bootstrapped admin. Duplicate
 * username/email coverage for ordinary shoppers is not enough: the seeded
 * {@code admin} / {@code admin@radiance.local} account is the dashboard login.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BootstrapAdminRegistrationTakeoverHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registeringBootstrappedAdminUsername_isConflict_andDoesNotChangeAdminPassword()
            throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"email\":\"cov26.takeover@x.com\","
                                + "\"password\":\"attacker999\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "attacker999")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
    }

    @Test
    void registeringBootstrappedAdminEmail_isConflict_andDoesNotCreateAttackerUsername()
            throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov26admintheft\",\"email\":\"admin@radiance.local\","
                                + "\"password\":\"thiefpass1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov26admintheft", "thiefpass1")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());
    }
}
