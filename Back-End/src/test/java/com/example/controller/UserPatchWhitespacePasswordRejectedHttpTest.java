package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Self-service PATCH with a whitespace-only password must not rotate
 * credentials. {@code updateUser} rejects any non-empty password field so
 * callers use the dedicated password path. Complements empty-password PATCH
 * (PR #16) and a normal password PATCH 400 (PR #13).
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserPatchWhitespacePasswordRejectedHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ownerPatchWhitespacePassword_doesNotChangeBasic() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov30pwspace\",\"email\":\"cov30pwspace@x.com\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/users/" + id)
                        .with(httpBasic("cov30pwspace", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"        \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov30pwspace", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov30pwspace"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov30pwspace", "        ")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/username/cov30pwspace")
                        .with(httpBasic("cov30pwspace", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov30pwspace"))
                .andExpect(jsonPath("$.email").value("cov30pwspace@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
