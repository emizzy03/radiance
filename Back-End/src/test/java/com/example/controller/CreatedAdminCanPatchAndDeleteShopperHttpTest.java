package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * A second admin minted through {@code POST /api/users/admin/{username}} must
 * be able to PATCH and DELETE a registered shopper. Complements seeded-admin
 * delete shopper (PR #30) and created-admin catalog writes (PR #35). Catches
 * {@code username.equals("admin")} on account mutations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreatedAdminCanPatchAndDeleteShopperHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdAdmin_canPatchShopperEmail_thenDeleteAccount() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov36shopper\",\"email\":\"cov36shopper@x.com\","
                                + "\"password\":\"shopper99\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long shopperId = objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/users/admin/cov36acctadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov36acctadmin@x.com\",\"password\":\"acctadmin99\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov36acctadmin", "acctadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(patch("/api/users/" + shopperId)
                        .with(httpBasic("cov36acctadmin", "acctadmin99"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov36shopper-new@x.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) shopperId))
                .andExpect(jsonPath("$.username").value("cov36shopper"))
                .andExpect(jsonPath("$.email").value("cov36shopper-new@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov36shopper", "shopper99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov36shopper"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("cov36shopper", "shopper99")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/users/" + shopperId)
                        .with(httpBasic("cov36acctadmin", "acctadmin99")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov36shopper", "shopper99")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/username/cov36shopper")
                        .with(httpBasic("cov36acctadmin", "acctadmin99")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());
    }
}
