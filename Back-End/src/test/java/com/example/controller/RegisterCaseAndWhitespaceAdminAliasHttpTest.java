package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Exact {@code admin} / {@code admin@radiance.local} registration and PATCH
 * collisions are covered elsewhere. The admin dashboard trims the login
 * username, so a case-variant, whitespace-padded, or lookalike account that
 * is allowed to exist must never receive {@code ROLE_ADMIN} and must never
 * change the seeded Basic credentials.
 *
 * <p>Usernames are currently case-sensitive and unique as stored; this test
 * does not require registration of aliases to fail, only that the dashboard
 * login stays the seeded admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegisterCaseAndWhitespaceAdminAliasHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private int registerStatus(String username, String email, String password) throws Exception {
        String body = String.format(
                "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                username, email, password);
        return mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private void assertBootstrapAdminUnchanged() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());
    }

    private void assertAliasIsNotAdmin(String username, String password) throws Exception {
        mockMvc.perform(get("/api/auth/me").with(httpBasic(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic(username, password)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", password)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_caseVariantAdminUsernameDoesNotTakeDashboard() throws Exception {
        int status = registerStatus("Admin", "cov28admincase@x.com", "aliaspass1");
        assertBootstrapAdminUnchanged();

        if (status == 201) {
            assertAliasIsNotAdmin("Admin", "aliaspass1");
        } else {
            org.junit.jupiter.api.Assertions.assertTrue(
                    status >= 400,
                    "rejected case-variant admin username must not be 2xx; got " + status);
            mockMvc.perform(get("/api/auth/me").with(httpBasic("Admin", "aliaspass1")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/auth/me").with(httpBasic("ADMIN", "admin12345")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_whitespacePaddedAdminUsernameDoesNotTakeDashboard() throws Exception {
        int trailing = registerStatus("admin ", "cov28adminpad@x.com", "aliaspass1");
        int leading = registerStatus(" admin", "cov28adminlead@x.com", "aliaspass1");
        assertBootstrapAdminUnchanged();

        if (trailing == 201) {
            assertAliasIsNotAdmin("admin ", "aliaspass1");
        }
        if (leading == 201) {
            assertAliasIsNotAdmin(" admin", "aliaspass1");
        }
        org.junit.jupiter.api.Assertions.assertTrue(
                trailing == 201 || trailing >= 400,
                "unexpected status for trailing-space admin username: " + trailing);
        org.junit.jupiter.api.Assertions.assertTrue(
                leading == 201 || leading >= 400,
                "unexpected status for leading-space admin username: " + leading);
    }

    @Test
    void register_caseVariantAdminEmailDoesNotChangeSeededEmail() throws Exception {
        int status = registerStatus("cov28mailcase", "Admin@Radiance.Local", "aliaspass1");
        assertBootstrapAdminUnchanged();

        mockMvc.perform(get("/api/users/username/admin").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@radiance.local"));

        if (status == 201) {
            mockMvc.perform(get("/api/auth/me").with(httpBasic("cov28mailcase", "aliaspass1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));
        } else {
            org.junit.jupiter.api.Assertions.assertTrue(
                    status >= 400,
                    "rejected case-variant admin email must not be 2xx; got " + status);
        }
    }

    @Test
    void ownerPatch_caseVariantAdminUsernameDoesNotGrantAdmin() throws Exception {
        String body = "{\"username\":\"cov28patchcase\",\"email\":\"cov28patchcase@x.com\",\"password\":\"shopperpass1\"}";
        MvcResult registered = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(registered.getResponse().getContentAsString());
        long id = node.get("id").asLong();

        MvcResult patched = mockMvc.perform(patch("/api/users/" + id)
                        .with(httpBasic("cov28patchcase", "shopperpass1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"AdMiN\"}"))
                .andReturn();
        int status = patched.getResponse().getStatus();

        assertBootstrapAdminUnchanged();
        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "shopperpass1")))
                .andExpect(status().isUnauthorized());

        if (status >= 200 && status < 300) {
            mockMvc.perform(get("/api/auth/me").with(httpBasic("AdMiN", "shopperpass1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));
            mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("AdMiN", "shopperpass1")))
                    .andExpect(status().isForbidden());
        } else {
            mockMvc.perform(get("/api/auth/me").with(httpBasic("cov28patchcase", "shopperpass1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("cov28patchcase"));
        }
    }
}
