package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CSRF is disabled because authentication is per-request HTTP Basic with
 * {@code SessionCreationPolicy.STATELESS}. If a JSESSIONID cookie started being
 * issued, browsers would attach an ambient credential and CSRF would become a
 * real vector for public checkout and user mutations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecuritySessionStatelessTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publicCheckout_doesNotIssueSessionCookie() throws Exception {
        String productJson =
                "{\"name\":\"Cov18StatelessLamp\",\"description\":\"d\",\"price\":4.00,\"quantity\":2,\"image\":\"/media/s.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MvcResult checkout = mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn();

        assertNoSessionCookie(checkout);
    }

    @Test
    void basicAuthMe_doesNotIssueSessionCookie() throws Exception {
        MvcResult me = mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();

        assertNoSessionCookie(me);
    }

    private static void assertNoSessionCookie(MvcResult result) {
        Collection<String> cookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(cookies)
                .noneMatch(value -> value != null && value.toUpperCase().contains("JSESSIONID"));
    }
}
