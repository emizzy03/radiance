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
 * A leftover {@code Authorization: Digest} header on the public catalog must
 * not hide products from the storefront. Complements leftover Bearer catalog
 * GET (PR #38) and leftover Digest checkout (PR #37). Catches an auth filter
 * that fail-closes unknown schemes on {@code permitAll}
 * {@code GET /api/products}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DigestLeftoverCatalogGetStillListsHttpTest {

    private static final String DIGEST =
            "Digest username=\"cov39\", realm=\"Radiance\", nonce=\"abc\", uri=\"/api/products\", response=\"deadbeef\"";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void leftoverDigest_doesNotHidePublicCatalog_orGrantAdminWrites() throws Exception {
        String create = "{\"name\":\"Cov39 Digest Catalog\",\"description\":\"keep-desc\","
                + "\"price\":5.50,\"quantity\":7,\"image\":\"/media/cov39-digest.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/auth/me").header("Authorization", DIGEST))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/purchases/daily-report").header("Authorization", DIGEST))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/products/" + id).header("Authorization", DIGEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.name").value("Cov39 Digest Catalog"))
                .andExpect(jsonPath("$.description").value("keep-desc"))
                .andExpect(jsonPath("$.price").value(5.50))
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.image").value("/media/cov39-digest.png"));

        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/products").header("Authorization", DIGEST))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode listed = rowForProduct(list, id);
        Assertions.assertNotNull(listed, "leftover Digest catalog GET must still list the SKU");
        Assertions.assertEquals("Cov39 Digest Catalog", listed.path("name").asText());
        Assertions.assertEquals(7, listed.path("quantity").asInt());
        Assertions.assertEquals("keep-desc", listed.path("description").asText());
        Assertions.assertEquals("/media/cov39-digest.png", listed.path("image").asText());

        mockMvc.perform(patch("/api/products/" + id)
                        .header("Authorization", DIGEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cov39 Stolen\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov39 Digest Catalog"))
                .andExpect(jsonPath("$.quantity").value(7));
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
