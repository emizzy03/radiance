package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A second admin created through the dashboard endpoint must be able to run
 * the same catalog + report flow as the bootstrap admin. Complements
 * createAdmin HTTP success + /me (PR #13) and createAdmin collision cases
 * (PR #28) without locking the current 404 mapping for duplicates.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreatedAdminCanManageCatalogAndReportHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdAdmin_canCreateProduct_recordPurchase_andReadReport() throws Exception {
        mockMvc.perform(post("/api/users/admin/cov31secondadmin")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cov31secondadmin@x.com\",\"password\":\"secondadmin1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov31secondadmin", "secondadmin1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cov31secondadmin"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));

        String create = "{\"name\":\"Cov31 Second Admin Sku\",\"description\":\"d\","
                + "\"price\":4.25,\"quantity\":8,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("cov31secondadmin", "secondadmin1"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov31 Second Admin Sku"))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/purchases")
                        .with(httpBasic("cov31secondadmin", "secondadmin1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(4.25));

        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("cov31secondadmin", "secondadmin1")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode match = null;
        int matches = 0;
        for (JsonNode row : objectMapper.readTree(report.getResponse().getContentAsString())) {
            if (row.path("productId").asLong() == id) {
                match = row;
                matches++;
            }
        }
        Assertions.assertEquals(1, matches);
        Assertions.assertNotNull(match);
        Assertions.assertEquals(2, match.path("totalQuantity").asInt());
        Assertions.assertEquals(
                0, new BigDecimal(match.path("totalRevenue").asText()).compareTo(new BigDecimal("8.50")));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk());
    }
}
