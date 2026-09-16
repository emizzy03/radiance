package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
 * Admin {@code GET /api/users} must return account identifiers without
 * passwords or roles. {@code User.roles} is {@code @JsonIgnore} both to hide
 * privileges and to avoid {@code LazyInitializationException} with
 * {@code open-in-view: false}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserListingRedactionHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listAllUsers_includesEmailAndOmitsPasswordAndRoles() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"listredact20\",\"email\":\"listredact20@x.com\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/users").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem("listredact20")))
                .andReturn();

        JsonNode users = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(users.isArray()).isTrue();

        JsonNode listed = null;
        for (JsonNode u : users) {
            if ("listredact20".equals(u.path("username").asText())) {
                listed = u;
                break;
            }
        }
        assertThat(listed).isNotNull();
        assertThat(listed.get("email").asText()).isEqualTo("listredact20@x.com");
        assertThat(listed.has("password")).isFalse();
        assertThat(listed.has("roles")).isFalse();
        assertThat(listed.has("id")).isTrue();
    }
}
