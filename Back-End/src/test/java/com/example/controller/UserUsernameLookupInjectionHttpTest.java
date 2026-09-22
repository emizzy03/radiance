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

/**
 * Username lookup is an exact match on a path variable. SQL fragments and LIKE
 * wildcards must not dump accounts or leak the admin profile to a shopper.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserUsernameLookupInjectionHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private void register(String username, String email) throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + email
                                + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void shopper_sqlLikeUsername_isForbiddenAndDoesNotLeakAdmin() throws Exception {
        register("cov26lookupshop", "cov26lookupshop@x.com");

        mockMvc.perform(get("/api/users/username/{username}", "' OR '1'='1")
                        .with(httpBasic("cov26lookupshop", "password123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/username/{username}", "admin")
                        .with(httpBasic("cov26lookupshop", "password123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_wildcardAndSqlLikeUsernames_areNotFoundNotAUserList() throws Exception {
        MvcResult percent = mockMvc.perform(get("/api/users/username/{username}", "%")
                        .with(httpBasic("admin", "admin12345")))
                .andReturn();
        int percentStatus = percent.getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(percentStatus == 400 || percentStatus == 404,
                "wildcard lookup must not dump users; got " + percentStatus);
        org.junit.jupiter.api.Assertions.assertFalse(
                percent.getResponse().getContentAsString().trim().startsWith("["),
                "wildcard lookup must not return a user array");

        mockMvc.perform(get("/api/users/username/{username}", "' OR '1'='1")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/users/username/admin")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());
    }
}
