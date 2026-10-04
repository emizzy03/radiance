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
 * A leftover {@code Authorization: Bearer} header on the public catalog must
 * not hide products from the storefront. Complements leftover Bearer checkout
 * (PR #36) and leftover Bearer registration (PR #37). Catches a JWT filter
 * that fail-closes {@code permitAll} {@code GET /api/products}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BearerLeftoverCatalogGetStillListsHttpTest {

    private static final String BEARER = "Bearer leftover.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void leftoverBearer_doesNotHidePublicCatalog_orGrantAdminWrites() throws Exception {
        String create = "{\"name\":\"Cov38 Bearer Catalog\",\"description\":\"keep-desc\","
                + "\"price\":4.50,\"quantity\":8,\"image\":\"/media/cov38-bearer.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/auth/me").header("Authorization", BEARER))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/purchases/daily-report").header("Authorization", BEARER))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/products/" + id).header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.name").value("Cov38 Bearer Catalog"))
                .andExpect(jsonPath("$.description").value("keep-desc"))
                .andExpect(jsonPath("$.price").value(4.50))
                .andExpect(jsonPath("$.quantity").value(8))
                .andExpect(jsonPath("$.image").value("/media/cov38-bearer.png"));

        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/products").header("Authorization", BEARER))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode listed = rowForProduct(list, id);
        Assertions.assertNotNull(listed, "leftover Bearer catalog GET must still list the SKU");
        Assertions.assertEquals("Cov38 Bearer Catalog", listed.path("name").asText());
        Assertions.assertEquals(8, listed.path("quantity").asInt());
        Assertions.assertEquals("keep-desc", listed.path("description").asText());
        Assertions.assertEquals("/media/cov38-bearer.png", listed.path("image").asText());

        mockMvc.perform(patch("/api/products/" + id)
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cov38 Stolen\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov38 Bearer Catalog"))
                .andExpect(jsonPath("$.quantity").value(8));
    }

    private static JsonNode rowForProduct(JsonNode list, long productId) {
        for (JsonNode row : list) {
            if (row.path("id").asLong() == productId) {
                return row;
            }
        }
        return null;
    }
}
