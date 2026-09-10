package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Registration validation, admin account deletion, and role-assignment 404s
 * that are not covered by the existing IDOR/mass-assignment suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserHttpEdgeTest {

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
    void registration_missingUsernameIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nouser@x.com\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void registration_missingEmailIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"noemailuser\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void deleteUser_adminCanDeleteAnotherAccount() throws Exception {
        long id = register("deleteme1", "deleteme1@x.com", "password123");

        mockMvc.perform(delete("/api/users/" + id).with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/username/deleteme1").with(user("boss").roles("ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("deleteme1", "password123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteUser_unknownIdIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/users/999999").with(user("boss").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUser_unknownIdIsNotFound() throws Exception {
        mockMvc.perform(patch("/api/users/999999").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@x.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignRole_unknownUserIsNotFound() throws Exception {
        mockMvc.perform(post("/api/users/999999/roles/ROLE_ADMIN")
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignRole_unknownRoleIsNotFound() throws Exception {
        long id = register("noroleyet", "noroleyet@x.com", "password123");
        mockMvc.perform(post("/api/users/" + id + "/roles/ROLE_DOES_NOT_EXIST")
                        .with(user("boss").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
