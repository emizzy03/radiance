package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A second admin minted through {@code POST /api/users/admin/{username}} must
 * be able to email-only PATCH a shopper with
 * {@code Content-Type: application/json;charset=UTF-8}. Complements
 * created-admin email PATCH (PR #36) and created-admin catalog charset PATCH
 * (PR #38). Catches {@code username.equals("admin")} on charset account PATCH
 * or a consumes matcher that only accepts the seeded dashboard principal.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreatedAdminShopperEmailPatchJsonCharsetUtf8HttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdAdmin_emailOnlyPatch_withJsonCharsetUtf8_keepsBasicAndFreesOldEmail() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov39charsetshop\",\"email\":\"cov39charsetshop@x.com\","
                                + "\"password\":\"shopper99\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long shopperId = objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/users/admin/cov39emailadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov39emailadmin@x.com\",\"password\":\"emailadmin99\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov39emailadmin", "emailadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(patch("/api/users/" + shopperId)
                        .with(httpBasic("cov39emailadmin", "emailadmin99"))
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                        .content("{\"email\":\"cov39charsetshop-new@x.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) shopperId))
                .andExpect(jsonPath("$.username").value("cov39charsetshop"))
                .andExpect(jsonPath("$.email").value("cov39charsetshop-new@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov39charsetshop", "shopper99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov39charsetshop"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));
        mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("cov39charsetshop", "shopper99")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/username/cov39charsetshop")
                        .with(httpBasic("cov39emailadmin", "emailadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) shopperId))
                .andExpect(jsonPath("$.email").value("cov39charsetshop-new@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov39emailreuse\",\"email\":\"cov39charsetshop@x.com\","
                                + "\"password\":\"shopper99\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cov39emailreuse"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov39emailsteal\",\"email\":\"cov39charsetshop-new@x.com\","
                                + "\"password\":\"shopper99\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
    }
}
