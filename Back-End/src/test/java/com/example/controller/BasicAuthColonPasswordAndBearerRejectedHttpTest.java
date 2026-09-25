package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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

/**
 * HTTP Basic splits {@code user:password} on the first colon, so a password
 * that itself contains {@code :} must still authenticate. A Bearer token is
 * not an accepted scheme and must not reach admin reporting.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BasicAuthColonPasswordAndBearerRejectedHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registrationPasswordContainingColon_authenticatesViaBasic() throws Exception {
        String body = "{\"username\":\"cov29colon\",\"email\":\"cov29colon@x.com\","
                + "\"password\":\"p@ss:word1\"}";
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cov29colon"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov29colon", "p@ss:word1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov29colon"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("cov29colon", "p@ss:word1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void bearerToken_onDailyReportIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/purchases/daily-report")
                        .header("Authorization", "Bearer admin12345"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/purchases/daily-report")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.x"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer admin12345"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());
    }
}
