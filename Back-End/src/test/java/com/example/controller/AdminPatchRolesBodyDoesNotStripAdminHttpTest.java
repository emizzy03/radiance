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
 * {@code User.roles} is {@code @JsonIgnore}: a PATCH body must not grant or
 * strip authorities. A created admin who PATCHes {@code roles:[]} (or a
 * {@code ROLE_USER} array) must still authenticate as {@code ROLE_ADMIN} and
 * reach the daily report. Complements registration mass-assignment on main
 * and owner PATCH self-grant (PR #17). Catches dropping {@code @JsonIgnore}
 * or applying roles inside {@code updateUser}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminPatchRolesBodyDoesNotStripAdminHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdAdmin_patchRolesBody_keepsRoleAdmin() throws Exception {
        mockMvc.perform(post("/api/users/admin/cov33keepadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov33keepadmin@x.com\",\"password\":\"keepadmin1\"}"))
                .andExpect(status().isOk());

        MvcResult lookedUp = mockMvc.perform(get("/api/users/username/cov33keepadmin")
                        .with(httpBasic("cov33keepadmin", "keepadmin1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andReturn();
        long id = objectMapper.readTree(lookedUp.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/users/" + id)
                        .with(httpBasic("cov33keepadmin", "keepadmin1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov33keepadmin"))
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov33keepadmin", "keepadmin1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov33keepadmin"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());

        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("cov33keepadmin", "keepadmin1")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/users/" + id)
                        .with(httpBasic("cov33keepadmin", "keepadmin1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[{\"name\":\"ROLE_USER\"}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov33keepadmin", "keepadmin1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
    }
}
