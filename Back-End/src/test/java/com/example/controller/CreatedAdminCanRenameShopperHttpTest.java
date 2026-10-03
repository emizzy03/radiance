package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
 * A second admin minted through {@code POST /api/users/admin/{username}} must
 * be able to PATCH a shopper username. Complements created-admin email PATCH
 * + delete (PR #36) and seeded-admin username rename (PR #29). Catches
 * {@code username.equals("admin")} on account username mutations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreatedAdminCanRenameShopperHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdAdmin_canPatchShopperUsername_withoutChangingPasswordOrRoles() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov37oldname\",\"email\":\"cov37oldname@x.com\","
                                + "\"password\":\"shopper99\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long shopperId = objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/users/admin/cov37renameadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov37renameadmin@x.com\",\"password\":\"renameadmin99\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov37renameadmin", "renameadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(patch("/api/users/" + shopperId)
                        .with(httpBasic("cov37renameadmin", "renameadmin99"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov37newname\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) shopperId))
                .andExpect(jsonPath("$.username").value("cov37newname"))
                .andExpect(jsonPath("$.email").value("cov37oldname@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov37oldname", "shopper99")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/username/cov37oldname")
                        .with(httpBasic("cov37renameadmin", "renameadmin99")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov37newname", "shopper99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov37newname"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/users/username/cov37newname")
                        .with(httpBasic("cov37renameadmin", "renameadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) shopperId))
                .andExpect(jsonPath("$.email").value("cov37oldname@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("cov37newname", "shopper99")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());
    }
}
