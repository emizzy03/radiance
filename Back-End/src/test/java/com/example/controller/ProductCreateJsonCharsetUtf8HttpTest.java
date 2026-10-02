package com.example.controller;

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
 * The admin dashboard may send {@code Content-Type: application/json;charset=UTF-8}
 * on catalog create. That media type must still persist the SKU on the public
 * catalog. Complements registration charset (PR #35) and checkout charset
 * (PR #26). Catches a consumes matcher that only accepts bare
 * {@code application/json}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductCreateJsonCharsetUtf8HttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void productCreate_withJsonCharsetUtf8_appearsOnPublicCatalog() throws Exception {
        String body = "{\"name\":\"Cov36 Charset Sku\",\"description\":\"keep\","
                + "\"price\":4.50,\"quantity\":7,\"image\":\"/media/cov36-charset.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov36 Charset Sku"))
                .andExpect(jsonPath("$.price").value(4.50))
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.image").value("/media/cov36-charset.png"))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov36 Charset Sku"))
                .andExpect(jsonPath("$.description").value("keep"))
                .andExpect(jsonPath("$.price").value(4.50))
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.image").value("/media/cov36-charset.png"));

        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/products"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode listed = rowForProduct(list, id);
        Assertions.assertNotNull(listed, "charset create must appear on the public catalog list");
        Assertions.assertEquals("Cov36 Charset Sku", listed.path("name").asText());
        Assertions.assertEquals(7, listed.path("quantity").asInt());
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
