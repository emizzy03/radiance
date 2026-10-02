package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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
 * A CJK/emoji username must round-trip through public registration, admin
 * username lookup, and HTTP Basic {@code /me}. Complements unicode product
 * names (PR #35). Catches a Latin-1 username column or Basic decoder that
 * drops non-ASCII principals.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UnicodeUsernameRegistrationBasicHttpTest {

    private static final String USERNAME = "Cov36ユーザー🌸";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unicodeUsername_registers_looksUp_andAuthenticatesViaBasic() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", USERNAME,
                "email", "cov36unicode@x.com",
                "password", "unic0de99"));

        MvcResult created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("cov36unicode@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andReturn();
        JsonNode createdNode = objectMapper.readTree(
                created.getResponse().getContentAsString(StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertEquals(USERNAME, createdNode.path("username").asText());

        mockMvc.perform(get("/api/users/username/" + USERNAME)
                        .with(httpBasic("admin", "admin12345"))
                        .characterEncoding(StandardCharsets.UTF_8.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.email").value("cov36unicode@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic(USERNAME, "unic0de99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic(USERNAME, "unic0de99")))
                .andExpect(status().isForbidden());
    }
}
