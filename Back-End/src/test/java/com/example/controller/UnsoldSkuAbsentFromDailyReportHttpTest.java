package com.example.controller;

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
 * The admin daily report is a sales aggregate ({@code FROM Purchase JOIN product}),
 * not a catalog dump. An unsold SKU must not appear as a $0 row next to a
 * sibling that actually sold. Complements two-SKU same-name rows (PR #24) and
 * failed-checkout rows (PR #24).
 */
@SpringBootTest
@AutoConfigureMockMvc
class UnsoldSkuAbsentFromDailyReportHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dailyReport_omitsUnsoldSku_andKeepsSoldSibling() throws Exception {
        long soldId = createProduct("Cov31 Sold Lamp", "8.50", 6);
        long unsoldId = createProduct("Cov31 Unsold Mug", "3.00", 4);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + soldId + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(8.50));

        mockMvc.perform(get("/api/products/" + unsoldId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.name").value("Cov31 Unsold Mug"));

        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode soldRow = null;
        int soldMatches = 0;
        int unsoldMatches = 0;
        for (JsonNode row : objectMapper.readTree(report.getResponse().getContentAsString())) {
            if (row.path("productId").asLong() == soldId) {
                soldRow = row;
                soldMatches++;
            }
            if (row.path("productId").asLong() == unsoldId) {
                unsoldMatches++;
            }
        }

        Assertions.assertEquals(1, soldMatches, "sold SKU must appear once on the daily report");
        Assertions.assertEquals(0, unsoldMatches, "unsold catalog SKU must not appear on the daily report");
        Assertions.assertNotNull(soldRow);
        Assertions.assertEquals(2, soldRow.path("totalQuantity").asInt());
        Assertions.assertEquals(
                0, new BigDecimal(soldRow.path("totalRevenue").asText()).compareTo(new BigDecimal("17.00")));
        Assertions.assertEquals("Cov31 Sold Lamp", soldRow.path("productName").asText());
    }

    private long createProduct(String name, String price, int quantity) throws Exception {
        String create = "{\"name\":\"" + name + "\",\"description\":\"d\","
                + "\"price\":" + price + ",\"quantity\":" + quantity + ",\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }
}
