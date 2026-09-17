package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
 * An empty JSON PATCH on a user must be a no-op: username, email, and
 * credentials stay put. Copying nulls from the body would rename/blank the
 * account and invalidate Basic auth.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserEmptyPatchHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void owner_emptyPatchDoesNotChangeUsernameEmailOrPassword() throws Exception {
        String body = "{\"username\":\"cov21emptypatch\",\"email\":\"cov21emptypatch@x.com\","
                + "\"password\":\"password123\"}";
        MvcResult created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = node.get("id").asLong();

        mockMvc.perform(patch("/api/users/" + id).with(user("cov21emptypatch").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.username").value("cov21emptypatch"))
                .andExpect(jsonPath("$.email").value("cov21emptypatch@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov21emptypatch", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov21emptypatch"));

        mockMvc.perform(get("/api/users/username/cov21emptypatch")
                        .with(httpBasic("cov21emptypatch", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov21emptypatch@x.com"));
    }
}
