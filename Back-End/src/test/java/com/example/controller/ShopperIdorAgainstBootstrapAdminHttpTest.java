package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A registered shopper with real Basic credentials must not PATCH or DELETE
 * the bootstrapped admin by numeric id. Complements shopper-vs-shopper IDOR
 * (PR #25) and registration-with-admin-id (PR #27).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShopperIdorAgainstBootstrapAdminHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shopperCannotPatchOrDeleteBootstrapAdminById() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov30idor\",\"email\":\"cov30idor@x.com\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        MvcResult listing = mockMvc.perform(get("/api/users")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        long adminId = -1L;
        for (JsonNode row : objectMapper.readTree(listing.getResponse().getContentAsString())) {
            if ("admin".equals(row.path("username").asText())) {
                adminId = row.path("id").asLong();
                break;
            }
        }
        Assertions.assertTrue(adminId > 0, "bootstrapped admin must appear in GET /api/users");

        mockMvc.perform(patch("/api/users/" + adminId)
                        .with(httpBasic("cov30idor", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacked-admin\",\"email\":\"hacked-admin@x.com\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/users/" + adminId)
                        .with(httpBasic("cov30idor", "password123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/username/admin").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.email").value("admin@radiance.local"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("hacked-admin", "password123")))
                .andExpect(status().isUnauthorized());
    }
}
