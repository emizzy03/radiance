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
 * The admin dashboard may send {@code Content-Type: application/json;charset=UTF-8}
 * on catalog PATCH. That media type must persist the edited fields on the public
 * catalog without blanking the others. Complements product create charset
 * (PR #36) and description/image-only PATCH (PR #19).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductPatchJsonCharsetUtf8HttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void productPatch_withJsonCharsetUtf8_persistsOnPublicCatalog() throws Exception {
        String create = "{\"name\":\"Cov37 Patch Original\",\"description\":\"keep-desc\","
                + "\"price\":8.00,\"quantity\":9,\"image\":\"/media/cov37-patch.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/products/" + id)
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                        .content("{\"name\":\"Cov37 Patch Charset\",\"price\":3.25,\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.name").value("Cov37 Patch Charset"))
                .andExpect(jsonPath("$.description").value("keep-desc"))
                .andExpect(jsonPath("$.price").value(3.25))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.image").value("/media/cov37-patch.png"));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov37 Patch Charset"))
                .andExpect(jsonPath("$.description").value("keep-desc"))
                .andExpect(jsonPath("$.price").value(3.25))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.image").value("/media/cov37-patch.png"));

        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/products"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode listed = rowForProduct(list, id);
        Assertions.assertNotNull(listed, "charset PATCH must appear on the public catalog list");
        Assertions.assertEquals("Cov37 Patch Charset", listed.path("name").asText());
        Assertions.assertEquals(5, listed.path("quantity").asInt());
        Assertions.assertEquals("keep-desc", listed.path("description").asText());
        Assertions.assertEquals("/media/cov37-patch.png", listed.path("image").asText());
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
