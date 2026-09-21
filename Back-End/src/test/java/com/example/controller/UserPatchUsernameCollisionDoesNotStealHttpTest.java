package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Self-service PATCH of username/email onto another account's unique values
 * must not steal that identity. Unique-constraint failures currently surface
 * as a generic 500; this test does not lock that status in — it only requires
 * that both original credentials keep working and the attacker password cannot
 * authenticate as the victim.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserPatchUsernameCollisionDoesNotStealHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private long register(String username, String email, String password) throws Exception {
        String body = String.format(
                "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                username, email, password);
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    @Test
    void ownerPatch_cannotTakeAnotherUsersUsername() throws Exception {
        long attackerId = register("cov25alice", "cov25alice@x.com", "alicepass1");
        register("cov25bob", "cov25bob@x.com", "bobpass123");

        MvcResult patched = mockMvc.perform(patch("/api/users/" + attackerId)
                        .with(httpBasic("cov25alice", "alicepass1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov25bob\"}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                patched.getResponse().getStatus() >= 400,
                "taking an in-use username must not succeed; got "
                        + patched.getResponse().getStatus());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov25bob", "bobpass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov25bob"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov25alice", "alicepass1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov25alice"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov25bob", "alicepass1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerPatch_cannotTakeAnotherUsersEmail() throws Exception {
        long attackerId = register("cov25mailalice", "cov25mailalice@x.com", "alicepass1");
        long victimId = register("cov25mailbob", "cov25mailbob@x.com", "bobpass123");

        MvcResult patched = mockMvc.perform(patch("/api/users/" + attackerId)
                        .with(httpBasic("cov25mailalice", "alicepass1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov25mailbob@x.com\"}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                patched.getResponse().getStatus() >= 400,
                "taking an in-use email must not succeed; got "
                        + patched.getResponse().getStatus());

        mockMvc.perform(get("/api/users/username/cov25mailalice")
                        .with(httpBasic("cov25mailalice", "alicepass1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov25mailalice@x.com"))
                .andExpect(jsonPath("$.username").value("cov25mailalice"));

        mockMvc.perform(get("/api/users/username/cov25mailbob")
                        .with(httpBasic("cov25mailbob", "bobpass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov25mailbob@x.com"))
                .andExpect(jsonPath("$.username").value("cov25mailbob"))
                .andExpect(jsonPath("$.id").value((int) victimId));
    }
}
