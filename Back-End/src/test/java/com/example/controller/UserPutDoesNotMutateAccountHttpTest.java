package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * User updates are PATCH-only. A PUT to {@code /api/users/{id}} must not
 * change email, username, or password. Complements PUT products (PR #20)
 * without locking in the current 500 from an unmapped method.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserPutDoesNotMutateAccountHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void putOwnAccount_isNotSuccess_andLeavesIdentityIntact() throws Exception {
        String register = "{\"username\":\"cov32putuser\",\"email\":\"cov32putuser@x.com\","
                + "\"password\":\"password123\"}";
        MvcResult created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MvcResult put = mockMvc.perform(put("/api/users/" + id)
                        .with(httpBasic("cov32putuser", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov32putuser-stolen\",\"email\":\"stolen-put@x.com\","
                                + "\"password\":\"newpass123\"}"))
                .andReturn();
        int status = put.getResponse().getStatus();
        Assertions.assertTrue(status < 200 || status >= 300, "PUT must not succeed; got " + status);
        String body = put.getResponse().getContentAsString();
        Assertions.assertFalse(body.contains("cov32putuser-stolen"));
        Assertions.assertFalse(body.contains("stolen-put@x.com"));

        mockMvc.perform(get("/api/users/username/cov32putuser")
                        .with(httpBasic("cov32putuser", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.username").value("cov32putuser"))
                .andExpect(jsonPath("$.email").value("cov32putuser@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov32putuser", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov32putuser"));
        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov32putuser-stolen", "newpass123")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/username/cov32putuser-stolen")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isNotFound());
    }
}
