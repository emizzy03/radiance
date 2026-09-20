package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
 * Daily-report JPQL groups by product id <em>and</em> name. Two catalog rows
 * that share a display name (common for size/color variants) must stay on
 * separate report rows with their own quantity and snapshot revenue. Grouping
 * only by name would silently merge SKUs and misstate admin revenue.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DailyReportDistinctSkuSameNameHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sameDisplayName_distinctSkusStayOnSeparateReportRows() throws Exception {
        long idA = createProduct("Cov24TwinLamp", "10.00", 10);
        long idB = createProduct("Cov24TwinLamp", "7.00", 10);
        org.junit.jupiter.api.Assertions.assertNotEquals(idA, idB);

        checkout(idA, 2);
        checkout(idB, 3);

        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rows = objectMapper.readTree(report.getResponse().getContentAsString());
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode row : rows) {
            if ("Cov24TwinLamp".equals(row.path("productName").asText())) {
                matches.add(row);
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(2, matches.size(),
                "same-named SKUs must not collapse into a single daily-report row");

        JsonNode rowA = findByProductId(matches, idA);
        JsonNode rowB = findByProductId(matches, idB);
        org.junit.jupiter.api.Assertions.assertNotNull(rowA, "report must include SKU A by productId");
        org.junit.jupiter.api.Assertions.assertNotNull(rowB, "report must include SKU B by productId");

        org.junit.jupiter.api.Assertions.assertEquals(2L, rowA.get("totalQuantity").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(3L, rowB.get("totalQuantity").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(
                0, new BigDecimal("20.00").compareTo(rowA.get("totalRevenue").decimalValue()));
        org.junit.jupiter.api.Assertions.assertEquals(
                0, new BigDecimal("21.00").compareTo(rowB.get("totalRevenue").decimalValue()));
    }

    private long createProduct(String name, String price, int quantity) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"description\":\"d\",\"price\":" + price
                + ",\"quantity\":" + quantity + ",\"image\":\"/media/twin.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private void checkout(long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated());
    }

    private static JsonNode findByProductId(List<JsonNode> rows, long productId) {
        for (JsonNode row : rows) {
            if (row.path("productId").asLong() == productId) {
                return row;
            }
        }
        return null;
    }
}
