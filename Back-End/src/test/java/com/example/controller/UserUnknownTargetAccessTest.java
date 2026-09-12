package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Non-admin callers must not learn whether an account exists. {@code isSelfOrAdmin}
 * fails closed for unknown ids (403, not 404), and username lookup rejects any
 * name that is not the caller's before hitting the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserUnknownTargetAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void patchUnknownUser_nonAdminIsForbiddenNotFound() throws Exception {
        mockMvc.perform(patch("/api/users/999999").with(user("nosypatch").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@x.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUnknownUser_nonAdminIsForbiddenNotFound() throws Exception {
        mockMvc.perform(delete("/api/users/999999").with(user("nosydelete").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUnknownUsername_nonAdminIsForbiddenNotFound() throws Exception {
        mockMvc.perform(get("/api/users/username/no-such-account-cov16")
                        .with(user("nosylookup").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
