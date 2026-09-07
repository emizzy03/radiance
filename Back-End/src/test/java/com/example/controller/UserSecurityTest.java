package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
 * Security regression tests for the user endpoints covering the fixes for
 * mass-assignment privilege escalation, broken access control / IDOR on
 * {@code /api/users/**}, admin-only listing/role-management, and non-serialized
 * passwords.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Registers a user through the public endpoint and returns the created id. */
    private long register(String username, String email, String password) throws Exception {
        String body = String.format(
                "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                username, email, password);
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                // password must never be serialized back to the client
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    // ---- 1. Mass-assignment privilege escalation ----------------------------
    @Test
    void registration_cannotSelfAssignAdminRole() throws Exception {
        // Attacker tries to grant themselves ROLE_ADMIN (by name and by id) and
        // to force a specific record id via mass assignment.
        String malicious = "{\"id\":9999,\"username\":\"evil\",\"email\":\"evil@x.com\","
                + "\"password\":\"password123\","
                + "\"roles\":[{\"id\":1,\"name\":\"ROLE_ADMIN\"}]}";

        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(malicious))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(node.get("id"));
        // id must be server-assigned, not the injected 9999
        org.junit.jupiter.api.Assertions.assertNotEquals(9999L, node.get("id").asLong());

        // Prove the account really has no admin authority: authenticate as it and
        // confirm /api/auth/me reports no ROLE_ADMIN.
        mockMvc.perform(get("/api/auth/me").with(httpBasic("evil", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));
    }

    // ---- 2/3. Listing all users is ADMIN-only, never leaks passwords --------
    @Test
    void listAllUsers_nonAdminForbidden() throws Exception {
        mockMvc.perform(get("/api/users").with(user("bob").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAllUsers_anonymousUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAllUsers_adminSucceedsAndHidesPasswords() throws Exception {
        register("listme", "listme@x.com", "password123");
        mockMvc.perform(get("/api/users").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("password"))));
    }

    // ---- 2. IDOR: update/delete restricted to owner or admin ----------------
    @Test
    void updateUser_cannotModifyAnotherUsersAccount() throws Exception {
        long victimId = register("victim1", "victim1@x.com", "password123");
        String patch = "{\"username\":\"hacked\",\"email\":\"hacked@x.com\"}";
        mockMvc.perform(patch("/api/users/" + victimId).with(user("attacker").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_ownerCanModifyOwnAccount() throws Exception {
        long id = register("owner1", "owner1@x.com", "password123");
        String patch = "{\"username\":\"owner1\",\"email\":\"owner1-new@x.com\"}";
        mockMvc.perform(patch("/api/users/" + id).with(user("owner1").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isOk());
    }

    @Test
    void updateUser_adminCanModifyAnyAccount() throws Exception {
        long id = register("target1", "target1@x.com", "password123");
        String patch = "{\"username\":\"target1\",\"email\":\"target1-admin@x.com\"}";
        mockMvc.perform(patch("/api/users/" + id).with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isOk());
    }

    @Test
    void deleteUser_cannotDeleteAnotherUsersAccount() throws Exception {
        long victimId = register("victim2", "victim2@x.com", "password123");
        mockMvc.perform(delete("/api/users/" + victimId).with(user("attacker").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUser_ownerCanDeleteOwnAccount() throws Exception {
        long id = register("owner2", "owner2@x.com", "password123");
        mockMvc.perform(delete("/api/users/" + id).with(user("owner2").roles("USER")))
                .andExpect(status().isOk());
    }

    // ---- 2. Role management endpoints are ADMIN-only ------------------------
    @Test
    void assignRole_nonAdminForbidden() throws Exception {
        long id = register("wannabe", "wannabe@x.com", "password123");
        mockMvc.perform(post("/api/users/" + id + "/roles/ROLE_ADMIN")
                        .with(user("wannabe").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAdmin_nonAdminForbidden() throws Exception {
        mockMvc.perform(post("/api/users/admin/newadmin").with(user("bob").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"na@x.com\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- getUserByUsername: self or admin only ------------------------------
    @Test
    void getUserByUsername_normalUserCannotReadOthers() throws Exception {
        register("secret", "secret@x.com", "password123");
        mockMvc.perform(get("/api/users/username/secret").with(user("nosy").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
