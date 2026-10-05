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
 * Leftover {@code Authorization: Negotiate} or {@code Token} headers on public
 * registration must not block account creation. Complements leftover Digest
 * registration (PR #38) and leftover Negotiate/Token checkout (PR #38).
 * Catches an auth filter that fail-closes unknown schemes on {@code permitAll}
 * {@code POST /api/users}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NegotiateLeftoverRegistrationStillCreatesHttpTest {

    private static final String NEGOTIATE =
            "Negotiate TlRMTVNTUAABAAAAB4IIogAAAAAAAAAAAAAAAAAAAAAKAKsBAQA=";
    private static final String TOKEN = "Token leftover.opaque.token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void leftoverNegotiateOrToken_doesNotBlockRegistration_orGrantAdmin() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", NEGOTIATE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", TOKEN))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/purchases/daily-report").header("Authorization", NEGOTIATE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/purchases/daily-report").header("Authorization", TOKEN))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/users")
                        .header("Authorization", NEGOTIATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov39negotiateshop\",\"email\":\"cov39negotiateshop@x.com\","
                                + "\"password\":\"shopper99\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cov39negotiateshop"))
                .andExpect(jsonPath("$.email").value("cov39negotiateshop@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov39negotiateshop", "shopper99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov39negotiateshop"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));
        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("cov39negotiateshop", "shopper99")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/users")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov39tokenshop\",\"email\":\"cov39tokenshop@x.com\","
                                + "\"password\":\"shopper99\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cov39tokenshop"))
                .andExpect(jsonPath("$.email").value("cov39tokenshop@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov39tokenshop", "shopper99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov39tokenshop"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));
        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("cov39tokenshop", "shopper99")))
                .andExpect(status().isForbidden());
    }
}
