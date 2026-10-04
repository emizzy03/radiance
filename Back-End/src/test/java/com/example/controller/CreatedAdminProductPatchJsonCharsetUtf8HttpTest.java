package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
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
 * A second admin minted through {@code POST /api/users/admin/{username}} must
 * be able to PATCH a catalog row with {@code Content-Type: application/json;charset=UTF-8}.
 * Complements seeded-admin PATCH charset (PR #37) and created-admin catalog
 * writes (PR #35). Catches {@code username.equals("admin")} on charset PATCH
 * or a consumes matcher that only accepts the seeded dashboard principal.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreatedAdminProductPatchJsonCharsetUtf8HttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdAdmin_productPatch_withJsonCharsetUtf8_persistsOnPublicCatalog() throws Exception {
        mockMvc.perform(post("/api/users/admin/cov38charsetadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov38charsetadmin@x.com\",\"password\":\"charsetadmin99\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov38charsetadmin", "charsetadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        String create = "{\"name\":\"Cov38 Charset Original\",\"description\":\"keep-desc\","
                + "\"price\":9.00,\"quantity\":11,\"image\":\"/media/cov38-charset.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/products/" + id)
                        .with(httpBasic("cov38charsetadmin", "charsetadmin99"))
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                        .content("{\"name\":\"Cov38 Charset Patched\",\"price\":2.75,\"quantity\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.name").value("Cov38 Charset Patched"))
                .andExpect(jsonPath("$.description").value("keep-desc"))
                .andExpect(jsonPath("$.price").value(2.75))
                .andExpect(jsonPath("$.quantity").value(6))
                .andExpect(jsonPath("$.image").value("/media/cov38-charset.png"));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov38 Charset Patched"))
                .andExpect(jsonPath("$.description").value("keep-desc"))
                .andExpect(jsonPath("$.price").value(2.75))
                .andExpect(jsonPath("$.quantity").value(6))
                .andExpect(jsonPath("$.image").value("/media/cov38-charset.png"));

        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/products"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode listed = rowForProduct(list, id);
        Assertions.assertNotNull(listed, "created-admin charset PATCH must appear on the public catalog list");
        Assertions.assertEquals("Cov38 Charset Patched", listed.path("name").asText());
        Assertions.assertEquals(6, listed.path("quantity").asInt());
        Assertions.assertEquals("keep-desc", listed.path("description").asText());
        Assertions.assertEquals("/media/cov38-charset.png", listed.path("image").asText());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
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
