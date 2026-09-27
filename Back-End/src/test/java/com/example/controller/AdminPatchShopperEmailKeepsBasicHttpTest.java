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
import org.springframework.beans.factory.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Admin dashboard can PATCH a shopper's email (UserSecurityTest uses a mock
 * ADMIN principal). Real Basic must keep the shopper's username/password,
 * free the old email for a new registration, and not grant ROLE_ADMIN.
 * Complements admin username rename (PR #29) and shopper email-collision
 * (PR #25).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminPatchShopperEmailKeepsBasicHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminPatchShopperEmail_keepsBasic_andFreesOldEmail() throws Exception {
        String register = "{\"username\":\"cov31emailshop\",\"email\":\"cov31emailshop@x.com\","
                + "\"password\":\"password123\"}";
        MvcResult created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/users/" + id)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov31emailshop-new@x.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.username").value("cov31emailshop"))
                .andExpect(jsonPath("$.email").value("cov31emailshop-new@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov31emailshop", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov31emailshop"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("cov31emailshop", "password123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/username/cov31emailshop")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov31emailshop-new@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov31emailreuse\",\"email\":\"cov31emailshop@x.com\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cov31emailreuse"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov31emailsteal\",\"email\":\"cov31emailshop-new@x.com\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
    }
}
