package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Username lookup and ownership checks are case-sensitive. Two distinct
 * accounts that differ only by case must not be able to read or PATCH each
 * other if the self-or-admin gate is loosened to {@code equalsIgnoreCase}.
 * Complements login case-sensitivity (PR #28) and shopper-vs-shopper IDOR
 * on different names (PR #25).
 */
@SpringBootTest
@AutoConfigureMockMvc
class UsernameLookupCaseSensitiveIdorHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private long register(String username, String email, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + email
                + "\",\"password\":\"" + password + "\"}";
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void caseVariantUsernames_cannotLookupOrPatchEachOther() throws Exception {
        long upperId = register("Cov32Victim", "cov32victim-a@x.com", "password123");
        long lowerId = register("cov32victim", "cov32victim-b@x.com", "password123");
        Assertions.assertNotEquals(upperId, lowerId);

        mockMvc.perform(get("/api/users/username/cov32victim")
                        .with(httpBasic("Cov32Victim", "password123")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/username/Cov32Victim")
                        .with(httpBasic("cov32victim", "password123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/username/Cov32Victim")
                        .with(httpBasic("Cov32Victim", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(upperId))
                .andExpect(jsonPath("$.email").value("cov32victim-a@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/users/username/cov32victim")
                        .with(httpBasic("cov32victim", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lowerId))
                .andExpect(jsonPath("$.email").value("cov32victim-b@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(patch("/api/users/" + lowerId)
                        .with(httpBasic("Cov32Victim", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hacked-upper@x.com\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/users/" + upperId)
                        .with(httpBasic("cov32victim", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hacked-lower@x.com\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/username/Cov32Victim")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov32victim-a@x.com"));
        mockMvc.perform(get("/api/users/username/cov32victim")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov32victim-b@x.com"));

        mockMvc.perform(get("/api/users/username/COV32VICTIM")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("Cov32Victim", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Cov32Victim"));
        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov32victim", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov32victim"));
    }
}
