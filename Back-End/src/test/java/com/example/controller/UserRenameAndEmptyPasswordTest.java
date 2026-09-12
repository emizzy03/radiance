package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Identity integrity after self-service PATCH: renaming must invalidate the old
 * Basic username, and an empty password field must not wipe or replace the hash.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserRenameAndEmptyPasswordTest {

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
    void owner_renameInvalidatesOldBasicUsername() throws Exception {
        long id = register("cov16renameold", "cov16renameold@x.com", "password123");

        mockMvc.perform(patch("/api/users/" + id).with(user("cov16renameold").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov16renamenew\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov16renamenew"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov16renameold", "password123")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov16renamenew", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov16renamenew"));

        mockMvc.perform(get("/api/users/username/cov16renamenew")
                        .with(httpBasic("cov16renamenew", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov16renamenew"))
                .andExpect(jsonPath("$.email").value("cov16renameold@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void owner_emptyPasswordPatchDoesNotChangeCredentials() throws Exception {
        long id = register("cov16emptypw", "cov16emptypw@x.com", "password123");

        mockMvc.perform(patch("/api/users/" + id).with(user("cov16emptypw").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"\",\"email\":\"cov16emptypw-new@x.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov16emptypw-new@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov16emptypw", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov16emptypw"));
    }
}
