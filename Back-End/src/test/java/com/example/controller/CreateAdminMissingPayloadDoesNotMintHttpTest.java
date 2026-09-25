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
 * {@code POST /api/users/admin/{username}} reads {@code email} and
 * {@code password} from a loosely typed map. A missing key must not mint a
 * loginable admin (including an empty-password account). Does not lock in
 * the current 404/500 mapping for {@code IllegalArgumentException} / NPE.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreateAdminMissingPayloadDoesNotMintHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private void assertBootstrapAdminIntact() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());
    }

    private void assertCreateAdminRejected(String username, String body) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/users/admin/" + username)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                created.getResponse().getStatus() >= 400,
                "incomplete createAdmin must not succeed; got " + created.getResponse().getStatus());

        mockMvc.perform(get("/api/auth/me").with(httpBasic(username, "password123")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").with(httpBasic(username, "")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic(username, "password123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAdmin_missingPasswordDoesNotMintLogin() throws Exception {
        assertCreateAdminRejected("cov29nopass", "{\"email\":\"cov29nopass@x.com\"}");
        assertBootstrapAdminIntact();
    }

    @Test
    void createAdmin_missingEmailDoesNotMintLogin() throws Exception {
        assertCreateAdminRejected("cov29noemail", "{\"password\":\"password123\"}");
        assertBootstrapAdminIntact();
    }

    @Test
    void createAdmin_emptyObjectDoesNotMintLogin() throws Exception {
        assertCreateAdminRejected("cov29emptyadmin", "{}");
        assertBootstrapAdminIntact();
    }
}
