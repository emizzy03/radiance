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
 * Self-service email-only PATCH must not blank or rename the username or
 * rotate the password. Complements owner username rename (PR #16), empty
 * {@code {}} PATCH (PR #21), and invalid-email PATCH (PR #18).
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserPatchEmailOnlyKeepsUsernameHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void owner_emailOnlyPatch_keepsUsernameAndPassword() throws Exception {
        String register = "{\"username\":\"cov31selfemail\",\"email\":\"cov31selfemail@x.com\","
                + "\"password\":\"password123\"}";
        MvcResult created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/users/" + id)
                        .with(httpBasic("cov31selfemail", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov31selfemail-new@x.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.username").value("cov31selfemail"))
                .andExpect(jsonPath("$.email").value("cov31selfemail-new@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov31selfemail", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov31selfemail"));

        mockMvc.perform(get("/api/users/username/cov31selfemail")
                        .with(httpBasic("cov31selfemail", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov31selfemail-new@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());
    }
}
