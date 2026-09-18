package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * The admin "Save" path always sends {@code image} from a hidden input, which
 * is empty when no file was uploaded. Storefront then substitutes a
 * placeholder. Create must succeed and the public list must still expose
 * name/price/quantity so the card can render.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminCreateWithoutImageHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createProduct_emptyImageIsListedOnPublicCatalog() throws Exception {
        String json = "{\"name\":\"Cov22NoImageCandle\",\"description\":\"soy\","
                + "\"price\":16.50,\"quantity\":8,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov22NoImageCandle"))
                .andExpect(jsonPath("$.price").value(16.50))
                .andExpect(jsonPath("$.quantity").value(8))
                .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(
                "",
                objectMapper.readTree(created.getResponse().getContentAsString()).path("image").asText());

        MvcResult list = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode products = objectMapper.readTree(list.getResponse().getContentAsString());
        JsonNode match = null;
        for (JsonNode product : products) {
            if (product.path("id").asLong() == id) {
                match = product;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(match, "catalog list must include the no-image SKU");
        org.junit.jupiter.api.Assertions.assertEquals("Cov22NoImageCandle", match.get("name").asText());
        org.junit.jupiter.api.Assertions.assertEquals("soy", match.get("description").asText());
        org.junit.jupiter.api.Assertions.assertEquals(8, match.get("quantity").asInt());
        org.junit.jupiter.api.Assertions.assertEquals("", match.path("image").asText());

        mockMvc.perform(get("/api/products/" + id).with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov22NoImageCandle"))
                .andExpect(jsonPath("$.image").value(""));
    }
}
