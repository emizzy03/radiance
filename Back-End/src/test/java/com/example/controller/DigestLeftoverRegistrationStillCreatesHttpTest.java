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
 * A leftover {@code Authorization: Digest} header on public registration must
 * not block account creation. Complements leftover Digest checkout (PR #37)
 * and leftover Bearer registration (PR #37). Catches an auth filter that
 * fail-closes unknown schemes on {@code permitAll} {@code POST /api/users}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DigestLeftoverRegistrationStillCreatesHttpTest {

    private static final String DIGEST =
            "Digest username=\"cov38\", realm=\"Radiance\", nonce=\"abc\", uri=\"/api/users\", response=\"deadbeef\"";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void leftoverDigest_doesNotBlockRegistration_orGrantAdmin() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", DIGEST))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/users")
                        .header("Authorization", DIGEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov38digestshop\",\"email\":\"cov38digestshop@x.com\","
                                + "\"password\":\"shopper99\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cov38digestshop"))
                .andExpect(jsonPath("$.email").value("cov38digestshop@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov38digestshop", "shopper99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov38digestshop"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("cov38digestshop", "shopper99")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/purchases/daily-report")
                        .header("Authorization", DIGEST))
                .andExpect(status().isUnauthorized());
    }
}
