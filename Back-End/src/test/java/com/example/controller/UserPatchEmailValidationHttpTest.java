package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
 * Self-service PATCH uses the default Bean Validation group, so {@code @Email}
 * must reject malformed addresses and leave the stored email unchanged.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserPatchEmailValidationHttpTest {

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
    void owner_invalidEmailPatchIsRejectedAndDoesNotPersist() throws Exception {
        long id = register("cov18email", "cov18email@x.com", "password123");

        mockMvc.perform(patch("/api/users/" + id).with(user("cov18email").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/users/username/cov18email").with(httpBasic("cov18email", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov18email@x.com"));
    }

    @Test
    void owner_validEmailPatchPersists() throws Exception {
        long id = register("cov18emailok", "cov18emailok@x.com", "password123");

        mockMvc.perform(patch("/api/users/" + id).with(user("cov18emailok").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov18emailok-new@x.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cov18emailok-new@x.com"));
    }
}
