package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The seeded dashboard account is the exact username {@code admin}. Lookup of
 * {@code Admin} must not return that account, and {@code Admin}/{password}
 * must not authenticate. Complements shopper pair case-sensitivity (PR #32)
 * and {@code ADMIN}/{password} login 401 (PR #28).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeededAdminUsernameLookupCaseSensitiveHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void seededAdmin_lookupAndBasic_areExactCase() throws Exception {
        mockMvc.perform(get("/api/users/username/admin")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.email").value("admin@radiance.local"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        MvcResult mixedLookup = mockMvc.perform(get("/api/users/username/Admin")
                        .with(httpBasic("admin", "admin12345")))
                .andReturn();
        int mixedStatus = mixedLookup.getResponse().getStatus();
        Assertions.assertTrue(mixedStatus == 404 || mixedStatus == 200,
                "mixed-case admin lookup must be 404 or a different account, not a 5xx");
        if (mixedStatus == 200) {
            JsonNode body = objectMapper.readTree(mixedLookup.getResponse().getContentAsString());
            Assertions.assertNotEquals("admin@radiance.local", body.path("email").asText(),
                    "GET /username/Admin must not return the seeded dashboard email");
            Assertions.assertNotEquals("admin", body.path("username").asText());
            Assertions.assertTrue(body.path("password").isMissingNode());
        }

        mockMvc.perform(get("/api/auth/me").with(httpBasic("Admin", "admin12345")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());
    }
}
