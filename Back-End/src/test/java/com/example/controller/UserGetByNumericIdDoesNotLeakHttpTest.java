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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Users are looked up by username (self-or-admin) or listed (admin only).
 * There is no {@code GET /api/users/{id}} contract; a response that returns
 * another account (or a password) by numeric id would be an IDOR. Username
 * lookup for self/admin must keep working. Does not lock in 404 vs 405 vs 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserGetByNumericIdDoesNotLeakHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getUserByNumericId_doesNotReturnAccount() throws Exception {
        String body = "{\"username\":\"cov29idor\",\"email\":\"cov29idor@x.com\","
                + "\"password\":\"password123\"}";
        MvcResult created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MvcResult shopperGet = mockMvc.perform(get("/api/users/" + id)
                        .with(httpBasic("cov29idor", "password123")))
                .andReturn();
        assertNotUserPayload(shopperGet, "cov29idor", "shopper GET by id");

        MvcResult adminGet = mockMvc.perform(get("/api/users/" + id)
                        .with(httpBasic("admin", "admin12345")))
                .andReturn();
        assertNotUserPayload(adminGet, "cov29idor", "admin GET by id");

        mockMvc.perform(get("/api/users/username/cov29idor").with(httpBasic("cov29idor", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov29idor"))
                .andExpect(jsonPath("$.email").value("cov29idor@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/users/username/cov29idor").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov29idor"))
                .andExpect(jsonPath("$.email").value("cov29idor@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    private void assertNotUserPayload(MvcResult result, String username, String label) throws Exception {
        int status = result.getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(
                status >= 400,
                label + " must not be 2xx; got " + status);
        String raw = result.getResponse().getContentAsString();
        if (raw == null || raw.isBlank()) {
            return;
        }
        JsonNode node = objectMapper.readTree(raw);
        if (node.isArray()) {
            return;
        }
        org.junit.jupiter.api.Assertions.assertFalse(
                username.equals(node.path("username").asText("")) && node.has("email"),
                label + " leaked a user document: " + raw);
        org.junit.jupiter.api.Assertions.assertTrue(
                node.path("password").isMissingNode() || node.path("password").isNull(),
                label + " leaked a password field");
    }
}
