package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
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

/**
 * {@code POST /api/users/admin/{username}} is the dashboard "create admin"
 * path. Re-posting the seeded username or email must not reset the
 * bootstrapped password, change the seeded email, or mint a second login
 * that replaces {@code admin}/{@code admin12345}.
 *
 * <p>Duplicate username/email currently maps {@code IllegalArgumentException}
 * to HTTP 404; this test does not lock that status in.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreateAdminDoesNotResetBootstrapHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private void assertBootstrapAdminUnchanged() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(get("/api/users/username/admin").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.email").value("admin@radiance.local"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());
    }

    @Test
    void createAdmin_existingBootstrapUsernameDoesNotResetPassword() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/users/admin/admin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov28-reset@x.com\",\"password\":\"attackerpass1\"}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                created.getResponse().getStatus() >= 400,
                "re-creating the bootstrapped admin must not succeed; got "
                        + created.getResponse().getStatus());

        assertBootstrapAdminUnchanged();

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "attackerpass1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAdmin_existingBootstrapEmailDoesNotMintReplacementAdmin() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/users/admin/cov28newadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@radiance.local\",\"password\":\"attackerpass1\"}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                created.getResponse().getStatus() >= 400,
                "creating an admin with the seeded email must not succeed; got "
                        + created.getResponse().getStatus());

        assertBootstrapAdminUnchanged();

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov28newadmin", "attackerpass1")))
                .andExpect(status().isUnauthorized());
    }
}
