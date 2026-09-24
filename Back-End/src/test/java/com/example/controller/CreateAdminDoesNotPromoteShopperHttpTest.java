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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * createAdmin must refuse an existing shopper username or email. A collision
 * must not promote that shopper to {@code ROLE_ADMIN}, must not replace their
 * password, and must not leave a second loginable admin with the attacker
 * password.
 *
 * <p>Does not lock in the current 404 mapping for duplicate username/email.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreateAdminDoesNotPromoteShopperHttpTest {

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
    void createAdmin_existingShopperUsernameDoesNotPromoteOrResetPassword() throws Exception {
        register("cov28shopkeep", "cov28shopkeep@x.com", "shopperpass1");

        MvcResult created = mockMvc.perform(post("/api/users/admin/cov28shopkeep")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov28shopkeep-admin@x.com\",\"password\":\"attackerpass1\"}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                created.getResponse().getStatus() >= 400,
                "createAdmin on an existing shopper username must not succeed; got "
                        + created.getResponse().getStatus());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov28shopkeep", "shopperpass1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov28shopkeep"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov28shopkeep", "attackerpass1")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("cov28shopkeep", "shopperpass1")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/username/cov28shopkeep")
                        .with(httpBasic("cov28shopkeep", "shopperpass1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov28shopkeep@x.com"));
    }

    @Test
    void createAdmin_existingShopperEmailDoesNotCreateReplacementLogin() throws Exception {
        register("cov28mailtarget", "cov28mailtarget@x.com", "shopperpass1");

        MvcResult created = mockMvc.perform(post("/api/users/admin/cov28mailadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov28mailtarget@x.com\",\"password\":\"attackerpass1\"}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                created.getResponse().getStatus() >= 400,
                "createAdmin with an existing shopper email must not succeed; got "
                        + created.getResponse().getStatus());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov28mailtarget", "shopperpass1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov28mailtarget"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov28mailadmin", "attackerpass1")))
                .andExpect(status().isUnauthorized());
    }
}
