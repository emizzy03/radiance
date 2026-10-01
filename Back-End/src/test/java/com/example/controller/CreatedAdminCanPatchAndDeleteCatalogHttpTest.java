package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * be able to PATCH and DELETE catalog rows created by the seeded dashboard
 * admin. Complements createAdmin POST+report (PR #31) and shopper 403 on
 * catalog writes (PR #22). Catches {@code username.equals("admin")} on
 * product mutations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreatedAdminCanPatchAndDeleteCatalogHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdAdmin_canPatchSeededSku_andDeleteUnusedSibling() throws Exception {
        mockMvc.perform(post("/api/users/admin/cov35catadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov35catadmin@x.com\",\"password\":\"catadmin99\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov35catadmin", "catadmin99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        String seededSku = "{\"name\":\"Cov35 Seeded Sku\",\"description\":\"keep me\","
                + "\"price\":7.00,\"quantity\":6,\"image\":\"/media/cov35-seed.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(seededSku))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        String unusedSku = "{\"name\":\"Cov35 Unused Sibling\",\"description\":\"gone\","
                + "\"price\":1.00,\"quantity\":3,\"image\":\"\"}";
        MvcResult unusedCreated = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(unusedSku))
                .andExpect(status().isOk())
                .andReturn();
        long unusedId = objectMapper.readTree(unusedCreated.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(patch("/api/products/" + id)
                        .with(httpBasic("cov35catadmin", "catadmin99"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cov35 Patched Sku\",\"price\":8.50,\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.name").value("Cov35 Patched Sku"))
                .andExpect(jsonPath("$.price").value(8.50))
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.description").value("keep me"))
                .andExpect(jsonPath("$.image").value("/media/cov35-seed.png"));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov35 Patched Sku"))
                .andExpect(jsonPath("$.price").value(8.50))
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.description").value("keep me"));

        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/products"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode listed = rowForProduct(list, id);
        Assertions.assertNotNull(listed, "patched SKU must remain on the public catalog list");
        Assertions.assertEquals("Cov35 Patched Sku", listed.path("name").asText());
        Assertions.assertEquals(4, listed.path("quantity").asInt());

        mockMvc.perform(delete("/api/products/" + unusedId)
                        .with(httpBasic("cov35catadmin", "catadmin99")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + unusedId))
                .andExpect(status().isNotFound());

        JsonNode listAfterDelete = objectMapper.readTree(
                mockMvc.perform(get("/api/products"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        Assertions.assertNull(rowForProduct(listAfterDelete, unusedId),
                "deleted unused SKU must leave the public catalog list");
        Assertions.assertNotNull(rowForProduct(listAfterDelete, id),
                "patched SKU must survive sibling delete");

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
