package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
 * A leftover {@code Authorization: Bearer} header on public registration must
 * not block account creation. Complements leftover Bearer checkout (PR #36)
 * and Bearer 401 on {@code /me} (PR #29). Catches a JWT filter that fail-closes
 * {@code permitAll} {@code POST /api/users}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BearerLeftoverRegistrationStillCreatesHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void leftoverBearer_doesNotBlockRegistration_orGrantAdmin() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer leftover.jwt.token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer leftover.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov37bearershop\",\"email\":\"cov37bearershop@x.com\","
                                + "\"password\":\"shopper99\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cov37bearershop"))
                .andExpect(jsonPath("$.email").value("cov37bearershop@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov37bearershop", "shopper99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov37bearershop"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("cov37bearershop", "shopper99")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/purchases/daily-report")
                        .header("Authorization", "Bearer leftover.jwt.token"))
                .andExpect(status().isUnauthorized());
    }
}
