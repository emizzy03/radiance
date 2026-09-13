package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
 * Self-service PATCH must not accept mass-assigned roles or a client-supplied
 * id. Registration already covers create-time privilege escalation; this locks
 * the update path so an owner cannot grant themselves ROLE_ADMIN or retarget
 * another account by stuffing {@code id} into the body.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserPatchMassAssignmentHttpTest {

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
    void owner_patchCannotSelfAssignAdminRole() throws Exception {
        long id = register("cov17escalator", "cov17escalator@x.com", "password123");

        String malicious = "{\"email\":\"cov17escalator-new@x.com\","
                + "\"roles\":[{\"id\":1,\"name\":\"ROLE_ADMIN\"}]}";
        mockMvc.perform(patch("/api/users/" + id).with(user("cov17escalator").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(malicious))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov17escalator-new@x.com"))
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov17escalator", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov17escalator"))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));
    }

    @Test
    void owner_patchBodyIdDoesNotRetargetAnotherAccount() throws Exception {
        long ownerId = register("cov17owner", "cov17owner@x.com", "password123");
        long victimId = register("cov17victim", "cov17victim@x.com", "password123");

        String sneaky = "{\"id\":" + victimId + ",\"email\":\"cov17owner-new@x.com\"}";
        mockMvc.perform(patch("/api/users/" + ownerId).with(user("cov17owner").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(sneaky))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) ownerId))
                .andExpect(jsonPath("$.email").value("cov17owner-new@x.com"));

        mockMvc.perform(get("/api/users/username/cov17victim").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) victimId))
                .andExpect(jsonPath("$.email").value("cov17victim@x.com"));
    }
}
