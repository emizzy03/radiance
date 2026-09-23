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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shopper-vs-shopper unique-constraint PATCH coverage does not protect the
 * seeded dashboard login. A self-service username/email change onto
 * {@code admin} / {@code admin@radiance.local} must not succeed, must not
 * change admin credentials, and must not grant the attacker ROLE_ADMIN.
 *
 * <p>Unique-constraint failures currently surface as a generic 500; this
 * test does not lock that status in.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShopperPatchCannotStealBootstrapAdminHttpTest {

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
    void ownerPatch_cannotTakeBootstrappedAdminUsername() throws Exception {
        long attackerId = register("cov27adminthief", "cov27adminthief@x.com", "thiefpass1");

        MvcResult patched = mockMvc.perform(patch("/api/users/" + attackerId)
                        .with(httpBasic("cov27adminthief", "thiefpass1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\"}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                patched.getResponse().getStatus() >= 400,
                "taking the bootstrapped admin username must not succeed; got "
                        + patched.getResponse().getStatus());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov27adminthief", "thiefpass1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov27adminthief"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "thiefpass1")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());
    }

    @Test
    void ownerPatch_cannotTakeBootstrappedAdminEmail() throws Exception {
        long attackerId = register("cov27mailthief", "cov27mailthief@x.com", "thiefpass1");

        MvcResult patched = mockMvc.perform(patch("/api/users/" + attackerId)
                        .with(httpBasic("cov27mailthief", "thiefpass1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@radiance.local\"}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                patched.getResponse().getStatus() >= 400,
                "taking the bootstrapped admin email must not succeed; got "
                        + patched.getResponse().getStatus());

        mockMvc.perform(get("/api/users/username/cov27mailthief")
                        .with(httpBasic("cov27mailthief", "thiefpass1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov27mailthief@x.com"))
                .andExpect(jsonPath("$.username").value("cov27mailthief"));

        mockMvc.perform(get("/api/users/username/admin")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@radiance.local"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());
    }
}
