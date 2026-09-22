package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Two storefront/admin contracts that existing tests only cover indirectly:
 * browsers may send {@code application/json;charset=UTF-8} on checkout, and
 * admin Delete must drop the SKU from {@code GET /api/products} (the grid),
 * not only from GET-by-id.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutJsonCharsetAndCatalogDeleteListHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void checkout_acceptsJsonWithCharsetAndDecrementsStock() throws Exception {
        String json = "{\"name\":\"Cov26CharsetBowl\",\"description\":\"d\",\"price\":3.50,"
                + "\"quantity\":8,\"image\":\"/media/bowl.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                        .content("{\"productId\":" + id + ",\"quantity\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.unitPrice").value(3.50));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    void adminDelete_unusedSkuDisappearsFromPublicCatalogList() throws Exception {
        String json = "{\"name\":\"Cov26DeleteFromList\",\"description\":\"d\",\"price\":11.00,"
                + "\"quantity\":2,\"image\":\"/media/gone.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MvcResult before = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                catalogContains(before.getResponse().getContentAsString(), id),
                "created SKU must appear on the public list before delete");

        mockMvc.perform(delete("/api/products/" + id).with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());

        MvcResult after = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertFalse(
                catalogContains(after.getResponse().getContentAsString(), id),
                "deleted SKU must not remain on the public catalog list");

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isNotFound());
    }

    private boolean catalogContains(String body, long id) throws Exception {
        JsonNode list = objectMapper.readTree(body);
        for (JsonNode product : list) {
            if (product.path("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }
}
