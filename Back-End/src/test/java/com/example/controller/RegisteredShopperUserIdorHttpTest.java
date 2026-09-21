package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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
 * Registration creates an account with no roles. IDOR and admin-creation
 * gates must hold for that real Basic principal — mock {@code roles("USER")}
 * tests would still pass if {@code CustomUserDetailsService} accidentally
 * granted {@code ROLE_ADMIN} to every DB user.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegisteredShopperUserIdorHttpTest {

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
    void registeredShopperBasic_cannotModifyOrDeleteAnotherAccount() throws Exception {
        long victimId = register("cov25victim", "cov25victim@x.com", "victimpass");
        register("cov25attacker", "cov25attacker@x.com", "attacker1");

        mockMvc.perform(patch("/api/users/" + victimId)
                        .with(httpBasic("cov25attacker", "attacker1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hacked-cov25@x.com\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/users/" + victimId)
                        .with(httpBasic("cov25attacker", "attacker1")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/username/cov25victim")
                        .with(httpBasic("cov25attacker", "attacker1")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/username/cov25victim")
                        .with(httpBasic("cov25victim", "victimpass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov25victim@x.com"))
                .andExpect(jsonPath("$.username").value("cov25victim"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov25victim", "victimpass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov25victim"));
    }

    @Test
    void registeredShopperBasic_cannotCreateAdminOrGrantRoles() throws Exception {
        long selfId = register("cov25shopper", "cov25shopper@x.com", "shopper12");

        mockMvc.perform(post("/api/users/admin/cov25rogue")
                        .with(httpBasic("cov25shopper", "shopper12"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov25rogue@x.com\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/users/" + selfId + "/roles/ROLE_ADMIN")
                        .with(httpBasic("cov25shopper", "shopper12")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov25shopper", "shopper12")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov25shopper"))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov25rogue", "password123")))
                .andExpect(status().isUnauthorized());
    }
}
