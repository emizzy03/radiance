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
 * {@code User.id} is {@code READ_ONLY}, and the main-suite mass-assignment
 * test injects a fictional id {@code 9999}. That does not prove the real
 * overwrite: a client that first lists users and then registers with the
 * bootstrapped admin's actual id must not replace that dashboard account.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegisterDoesNotOverwriteAdminByIdHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registration_withBootstrappedAdminId_createsNewShopperAndLeavesAdminIntact()
            throws Exception {
        MvcResult listed = mockMvc.perform(get("/api/users")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode users = objectMapper.readTree(listed.getResponse().getContentAsString());
        Long adminId = null;
        for (JsonNode user : users) {
            if ("admin".equals(user.path("username").asText())) {
                adminId = user.path("id").asLong();
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(adminId, "bootstrapped admin must appear in GET /api/users");

        String body = "{\"id\":" + adminId + ",\"username\":\"cov27idsteal\","
                + "\"email\":\"cov27idsteal@x.com\",\"password\":\"attacker999\"}";
        MvcResult created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cov27idsteal"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        long newId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();
        org.junit.jupiter.api.Assertions.assertNotEquals(adminId.longValue(), newId,
                "registration must not reuse the admin row id");

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "attacker999")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov27idsteal", "attacker999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov27idsteal"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());
    }
}
