package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A dashboard admin created via {@code POST /api/users/admin/{username}} must
 * keep {@code ROLE_ADMIN} after an email-only PATCH of their own account.
 * Username and password stay the same so Basic login and daily-report access
 * survive the identity change. Complements createAdmin catalog access (PR #31)
 * and admin PATCH of a shopper email (PR #31).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreatedAdminPatchOwnEmailKeepsAdminHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdAdmin_patchOwnEmail_keepsRoleAdminAndPassword() throws Exception {
        mockMvc.perform(post("/api/users/admin/cov32secondadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov32secondadmin@x.com\",\"password\":\"secondadmin1\"}"))
                .andExpect(status().isOk());

        MvcResult lookedUp = mockMvc.perform(get("/api/users/username/cov32secondadmin")
                        .with(httpBasic("cov32secondadmin", "secondadmin1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov32secondadmin@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        long id = objectMapper.readTree(lookedUp.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/users/" + id)
                        .with(httpBasic("cov32secondadmin", "secondadmin1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov32secondadmin-new@x.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov32secondadmin"))
                .andExpect(jsonPath("$.email").value("cov32secondadmin-new@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov32secondadmin", "secondadmin1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov32secondadmin"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("cov32secondadmin", "secondadmin1")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/username/cov32secondadmin")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("cov32secondadmin-new@x.com"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
    }
}
