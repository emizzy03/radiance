package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-context security tests proving admin-only endpoints reject anonymous
 * (401) and non-admin (403) callers while allowing ROLE_ADMIN, and that the
 * static admin page assets are publicly served.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAdminEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String PRODUCT_JSON =
            "{\"name\":\"Test\",\"description\":\"d\",\"price\":9.99,\"quantity\":5,\"image\":\"i.png\"}";

    // ---- product create (ADMIN only) ----
    @Test
    void createProduct_anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProduct_nonAdminIsForbidden() throws Exception {
        mockMvc.perform(post("/api/products").with(user("bob").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProduct_adminSucceeds() throws Exception {
        mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isOk());
    }

    // ---- daily report (ADMIN only) ----
    @Test
    void dailyReport_anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/purchases/daily-report"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dailyReport_nonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/purchases/daily-report").with(user("bob").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void dailyReport_adminSucceeds() throws Exception {
        mockMvc.perform(get("/api/purchases/daily-report").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    // ---- public checkout ----
    @Test
    void recordPurchase_isPublic_butValidatesBody() throws Exception {
        // Anonymous is allowed to reach the endpoint (not 401/403); an unknown
        // product yields a 400 rather than an auth error.
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":999999,\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    // ---- real HTTP Basic auth against the DB-backed, bootstrapped admin ----
    // Regression test: authorities come from a lazily-loaded roles collection,
    // so authentication must run inside a transaction.
    @Test
    void bootstrappedAdmin_canAuthenticateWithBasicAndReachAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());
    }

    @Test
    void basicAuth_wrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    // ---- static admin page ----
    @Test
    void adminStaticAssetsAreServed() throws Exception {
        mockMvc.perform(get("/admin/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/app.js")).andExpect(status().isOk());
    }

    @Test
    void adminDirectoryForwardsToIndex() throws Exception {
        mockMvc.perform(get("/admin/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/index.html"));
    }
}
