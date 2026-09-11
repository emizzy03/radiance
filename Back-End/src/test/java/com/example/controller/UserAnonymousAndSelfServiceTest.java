package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Coarse auth gate on user mutations (anonymous 401 from the filter chain) and
 * self-service identity flows that IDOR tests currently only check as HTTP 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserAnonymousAndSelfServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private long register(String username, String email, String password) throws Exception {
        String body = String.format(
                "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                username, email, password);
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    @Test
    void anonymous_cannotPatchOrDeleteUsers() throws Exception {
        long id = register("anonvictim", "anonvictim@x.com", "password123");
        mockMvc.perform(patch("/api/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"stolen@x.com\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/users/" + id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymous_cannotLookupUsername() throws Exception {
        register("hiddenacct", "hiddenacct@x.com", "password123");
        mockMvc.perform(get("/api/users/username/hiddenacct"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymous_cannotCreateAdmin() throws Exception {
        mockMvc.perform(post("/api/users/admin/sneaky")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sneaky@x.com\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_patchPersistsAndSelfLookupHidesPassword() throws Exception {
        long id = register("selfpatch", "selfpatch@x.com", "password123");
        mockMvc.perform(patch("/api/users/" + id).with(user("selfpatch").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"selfpatch-new@x.com\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/username/selfpatch").with(httpBasic("selfpatch", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("selfpatch-new@x.com"))
                .andExpect(jsonPath("$.username").value("selfpatch"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());
    }

    @Test
    void registeredShopper_authMeReportsUsernameWithoutAdmin() throws Exception {
        register("shopperme", "shopperme@x.com", "password123");
        mockMvc.perform(get("/api/auth/me").with(httpBasic("shopperme", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("shopperme"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));
    }

    @Test
    void owner_selfDeleteInvalidatesBasicAuth() throws Exception {
        long id = register("goneuser", "goneuser@x.com", "password123");
        mockMvc.perform(get("/api/auth/me").with(httpBasic("goneuser", "password123")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/" + id).with(user("goneuser").roles("USER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("goneuser", "password123")))
                .andExpect(status().isUnauthorized());
    }
}
