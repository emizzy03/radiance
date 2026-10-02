package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * A second admin minted through {@code POST /api/users/admin/{username}} must
 * be able to list accounts and look up both a shopper and the seeded dashboard
 * by username. Complements seeded-admin listing (PR #23) and created-admin
 * catalog writes (PR #35). Catches {@code username.equals("admin")} on
 * {@code GET /api/users}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreatedAdminCanListAndLookupUsersHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdAdmin_canListUsers_andLookupShopperAndSeededAdmin() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cov36listed\",\"email\":\"cov36listed@x.com\","
                                + "\"password\":\"listed99x\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users/admin/cov36listadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov36listadmin@x.com\",\"password\":\"listadmin99\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov36listadmin", "listadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        mockMvc.perform(get("/api/users/username/cov36listed")
                        .with(httpBasic("cov36listadmin", "listadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov36listed"))
                .andExpect(jsonPath("$.email").value("cov36listed@x.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        mockMvc.perform(get("/api/users/username/admin")
                        .with(httpBasic("cov36listadmin", "listadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.email").value("admin@radiance.local"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/users").with(httpBasic("cov36listadmin", "listadmin99")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[*].password").doesNotExist())
                        .andExpect(jsonPath("$[*].roles").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode shopper = rowForUser(list, "cov36listed");
        JsonNode seeded = rowForUser(list, "admin");
        Assertions.assertNotNull(shopper, "created admin must see the registered shopper on GET /api/users");
        Assertions.assertEquals("cov36listed@x.com", shopper.path("email").asText());
        Assertions.assertTrue(shopper.path("id").asLong() > 0);
        Assertions.assertNotNull(seeded, "created admin must see the seeded dashboard account on GET /api/users");
        Assertions.assertEquals("admin@radiance.local", seeded.path("email").asText());

        mockMvc.perform(get("/api/users").with(httpBasic("cov36listed", "listed99x")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/username/admin").with(httpBasic("cov36listed", "listed99x")))
                .andExpect(status().isForbidden());
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
