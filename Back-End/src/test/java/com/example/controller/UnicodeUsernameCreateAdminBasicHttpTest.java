package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A CJK/emoji username minted through {@code POST /api/users/admin/{username}}
 * must authenticate via HTTP Basic and reach admin-only endpoints. Complements
 * unicode shopper registration (PR #36). Catches a Latin-1 username column or
 * path decoder that drops non-ASCII principals on createAdmin.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UnicodeUsernameCreateAdminBasicHttpTest {

    private static final String USERNAME = "Cov37管理者🌟";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unicodeUsername_createAdmin_authenticatesAndReachesDailyReport() throws Exception {
        mockMvc.perform(post("/api/users/admin/" + USERNAME)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content("{\"email\":\"cov37uniadmin@x.com\",\"password\":\"uniadmin99\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic(USERNAME, "uniadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic(USERNAME, "uniadmin99")))
                .andExpect(status().isOk());

        MvcResult lookup = mockMvc.perform(get("/api/users/username/" + USERNAME)
                        .with(httpBasic("admin", "admin12345"))
                        .characterEncoding(StandardCharsets.UTF_8.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov37uniadmin@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andReturn();
        JsonNode lookedUp = objectMapper.readTree(
                lookup.getResponse().getContentAsString(StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertEquals(USERNAME, lookedUp.path("username").asText());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
    }
}
