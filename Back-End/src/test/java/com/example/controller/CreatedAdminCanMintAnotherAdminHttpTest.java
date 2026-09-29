package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /api/users/admin/{username}} is authorized by {@code ROLE_ADMIN},
 * not by a hardcoded seeded username. A created admin must be able to mint a
 * further admin who can authenticate, see {@code ROLE_ADMIN} on {@code /me},
 * and GET the daily report. A registered shopper still cannot. Complements
 * createAdmin catalog access (PR #31). Catches checks like
 * {@code username.equals("admin")} on the create-admin path.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreatedAdminCanMintAnotherAdminHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createdAdmin_canCreateAnotherAdmin_shopperCannot() throws Exception {
        mockMvc.perform(post("/api/users/admin/cov33adminA")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov33adminA@x.com\",\"password\":\"adminApass1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov33adminA", "adminApass1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov33adminA"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(post("/api/users/admin/cov33adminB")
                        .with(httpBasic("cov33adminA", "adminApass1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov33adminB@x.com\",\"password\":\"adminBpass1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov33adminB", "adminBpass1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov33adminB"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());

        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("cov33adminB", "adminBpass1")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov33shopper\",\"email\":\"cov33shopper@x.com\","
                                + "\"password\":\"shopper123\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users/admin/cov33fromshopper")
                        .with(httpBasic("cov33shopper", "shopper123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov33fromshopper@x.com\",\"password\":\"shopperadm1\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov33fromshopper", "shopperadm1")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov33shopper", "shopper123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
    }
}
