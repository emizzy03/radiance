package com.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Role grant/revoke is ADMIN-only at the filter chain. Anonymous callers must
 * be 401 (not a leaked 404/200) even for unknown ids.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleEndpointAnonymousHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymous_cannotAssignRole() throws Exception {
        mockMvc.perform(post("/api/users/1/roles/ROLE_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymous_cannotRemoveRole() throws Exception {
        mockMvc.perform(delete("/api/users/1/roles/ROLE_ADMIN"))
                .andExpect(status().isUnauthorized());
    }
}
