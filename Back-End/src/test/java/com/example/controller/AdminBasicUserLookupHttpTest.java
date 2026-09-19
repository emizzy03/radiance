package com.example.controller;

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
 * User lookup/listing authorization must work with real HTTP Basic against the
 * bootstrapped admin (CustomUserDetailsService + {@code @PreAuthorize}), not
 * only with mock {@code roles("ADMIN")} / {@code roles("USER")}. A registered
 * shopper has empty roles, which is the production self-service path.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminBasicUserLookupHttpTest {

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
    void bootstrappedAdminBasic_canLookupShopperAndListUsersWithoutSecrets() throws Exception {
        register("cov23lookme", "cov23lookme@x.com", "password123");

        mockMvc.perform(get("/api/users/username/cov23lookme")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov23lookme"))
                .andExpect(jsonPath("$.email").value("cov23lookme@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/users").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username").value(org.hamcrest.Matchers.hasItem("cov23lookme")))
                .andExpect(jsonPath("$[*].password").doesNotExist())
                .andExpect(jsonPath("$[*].roles").doesNotExist());
    }

    @Test
    void registeredShopperBasic_cannotEnumerateAdminOrListUsers() throws Exception {
        register("cov23nosy", "cov23nosy@x.com", "password123");

        mockMvc.perform(get("/api/users/username/admin").with(httpBasic("cov23nosy", "password123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users").with(httpBasic("cov23nosy", "password123")))
                .andExpect(status().isForbidden());
    }
}
