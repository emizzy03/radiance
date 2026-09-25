package com.example.controller;

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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Admin PATCH of another account's username (the privileged Edit-user path)
 * must rotate the Basic principal. The old username cannot authenticate; the
 * new one can, with the same password, and is still not ROLE_ADMIN.
 * Complements owner self-rename (PR #16).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminRenameShopperInvalidatesOldBasicHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminRenameShopper_oldBasicFails_newBasicWorks() throws Exception {
        String body = "{\"username\":\"cov29oldname\",\"email\":\"cov29oldname@x.com\","
                + "\"password\":\"password123\"}";
        MvcResult created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov29oldname", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov29oldname"));

        mockMvc.perform(patch("/api/users/" + id)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov29newname\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov29newname"))
                .andExpect(jsonPath("$.email").value("cov29oldname@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov29oldname", "password123")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov29newname", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov29newname"))
                .andExpect(jsonPath("$.roles").isArray());

        mockMvc.perform(get("/api/users/username/cov29newname").with(httpBasic("cov29newname", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov29newname"))
                .andExpect(jsonPath("$.email").value("cov29oldname@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/users/username/cov29oldname").with(httpBasic("cov29newname", "password123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("cov29newname", "password123")))
                .andExpect(status().isForbidden());
    }
}
