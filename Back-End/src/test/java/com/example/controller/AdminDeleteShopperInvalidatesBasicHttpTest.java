package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Admin DELETE of a shopper must invalidate that account's Basic credentials
 * immediately, without disturbing the seeded dashboard login. Complements
 * self-delete (PR #15) and admin rename of a shopper (PR #29).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminDeleteShopperInvalidatesBasicHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminDeleteShopper_oldBasicIsUnauthorized() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov30delme\",\"email\":\"cov30delme@x.com\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov30delme", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov30delme"));

        mockMvc.perform(delete("/api/users/" + id).with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov30delme", "password123")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/username/cov30delme").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());
    }
}
