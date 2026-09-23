package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Self-delete is a supported shopper action (owner DELETE). After a hard
 * delete the unique username/email must be free so the person can register
 * again. Self-delete already proves the old Basic credential dies; this
 * covers the follow-up signup that the storefront registration form issues.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeletedUsernameCanReregisterHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void selfDelete_thenSameUsernameAndEmailCanRegisterAgainWithNewPassword() throws Exception {
        String body = "{\"username\":\"cov27rejoin\",\"email\":\"cov27rejoin@x.com\","
                + "\"password\":\"oldpass12\"}";
        MvcResult created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long firstId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(delete("/api/users/" + firstId)
                        .with(httpBasic("cov27rejoin", "oldpass12")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov27rejoin", "oldpass12")))
                .andExpect(status().isUnauthorized());

        String again = "{\"username\":\"cov27rejoin\",\"email\":\"cov27rejoin@x.com\","
                + "\"password\":\"newpass99\"}";
        MvcResult recreated = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(again))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cov27rejoin"))
                .andExpect(jsonPath("$.email").value("cov27rejoin@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        long secondId = objectMapper.readTree(recreated.getResponse().getContentAsString())
                .get("id").asLong();
        org.junit.jupiter.api.Assertions.assertNotEquals(firstId, secondId,
                "re-registration must allocate a new user id");

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov27rejoin", "newpass99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov27rejoin"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov27rejoin", "oldpass12")))
                .andExpect(status().isUnauthorized());
    }
}
