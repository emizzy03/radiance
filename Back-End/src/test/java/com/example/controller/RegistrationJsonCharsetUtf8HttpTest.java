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
 * Browsers often send {@code Content-Type: application/json;charset=UTF-8}
 * on registration. That media type must still create a loginable shopper.
 * Complements checkout {@code charset=UTF-8} (PR #26). Catches a consumes
 * matcher that only accepts bare {@code application/json}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationJsonCharsetUtf8HttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registration_withJsonCharsetUtf8_createsLoginableShopper() throws Exception {
        String body = "{\"username\":\"cov35charset\",\"email\":\"cov35charset@x.com\","
                + "\"password\":\"charset99\"}";
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cov35charset"))
                .andExpect(jsonPath("$.email").value("cov35charset@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov35charset", "charset99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov35charset"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("cov35charset", "charset99")))
                .andExpect(status().isForbidden());
    }
}
