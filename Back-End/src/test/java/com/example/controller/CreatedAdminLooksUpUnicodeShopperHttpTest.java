package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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
 * be able to look up a CJK/emoji shopper username. Complements unicode shopper
 * registration (PR #36, seeded-admin lookup) and unicode createAdmin (PR #37).
 * Catches {@code username.equals("admin")} on username lookup or a Latin-1
 * path decoder that drops non-ASCII principals for minted admins.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreatedAdminLooksUpUnicodeShopperHttpTest {

    private static final String USERNAME = "Cov38顧客🌺";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdAdmin_canLookupUnicodeShopper_withoutLeakingSecrets() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", USERNAME,
                "email", "cov38unishop@x.com",
                "password", "unic0de99"));

        MvcResult created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("cov38unishop@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andReturn();
        JsonNode createdNode = objectMapper.readTree(
                created.getResponse().getContentAsString(StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertEquals(USERNAME, createdNode.path("username").asText());
        long shopperId = createdNode.path("id").asLong();

        mockMvc.perform(post("/api/users/admin/cov38lookupadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov38lookupadmin@x.com\",\"password\":\"lookupadmin99\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov38lookupadmin", "lookupadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        MvcResult lookup = mockMvc.perform(get("/api/users/username/" + USERNAME)
                        .with(httpBasic("cov38lookupadmin", "lookupadmin99"))
                        .characterEncoding(StandardCharsets.UTF_8.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) shopperId))
                .andExpect(jsonPath("$.email").value("cov38unishop@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andReturn();
        JsonNode lookedUp = objectMapper.readTree(
                lookup.getResponse().getContentAsString(StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertEquals(USERNAME, lookedUp.path("username").asText());

        mockMvc.perform(get("/api/auth/me").with(httpBasic(USERNAME, "unic0de99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic(USERNAME, "unic0de99")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
    }
}
