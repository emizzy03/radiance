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
 * A second admin minted through {@code POST /api/users/admin/{username}} must
 * see a CJK/emoji shopper on {@code GET /api/users}. Complements created-admin
 * listing of ASCII shoppers (PR #36) and created-admin unicode username lookup
 * (PR #38). Catches {@code username.equals("admin")} on the list endpoint or a
 * Latin-1 JSON encoder that drops non-ASCII usernames for minted admins.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreatedAdminListsUnicodeShopperHttpTest {

    private static final String USERNAME = "Cov39顧客🌺";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdAdmin_userList_includesUnicodeShopper_withoutLeakingSecrets() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", USERNAME,
                "email", "cov39unilist@x.com",
                "password", "unic0de99"));

        MvcResult created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("cov39unilist@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andReturn();
        JsonNode createdNode = objectMapper.readTree(
                created.getResponse().getContentAsString(StandardCharsets.UTF_8));
        Assertions.assertEquals(USERNAME, createdNode.path("username").asText());
        long shopperId = createdNode.path("id").asLong();

        mockMvc.perform(post("/api/users/admin/cov39listadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov39listadmin@x.com\",\"password\":\"listadmin99\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov39listadmin", "listadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/users").with(httpBasic("cov39listadmin", "listadmin99")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[*].password").doesNotExist())
                        .andExpect(jsonPath("$[*].roles").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8));
        JsonNode shopper = rowForUser(list, USERNAME);
        JsonNode seeded = rowForUser(list, "admin");
        Assertions.assertNotNull(shopper, "created admin must see the unicode shopper on GET /api/users");
        Assertions.assertEquals(shopperId, shopper.path("id").asLong());
        Assertions.assertEquals("cov39unilist@x.com", shopper.path("email").asText());
        Assertions.assertNotNull(seeded, "created admin must still see the seeded dashboard account");
        Assertions.assertEquals("admin@radiance.local", seeded.path("email").asText());

        mockMvc.perform(get("/api/auth/me").with(httpBasic(USERNAME, "unic0de99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));
        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic(USERNAME, "unic0de99")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").with(httpBasic(USERNAME, "unic0de99")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
    }

    private static JsonNode rowForUser(JsonNode list, String username) {
        for (JsonNode row : list) {
            if (username.equals(row.path("username").asText())) {
                return row;
            }
        }
        return null;
    }
}
