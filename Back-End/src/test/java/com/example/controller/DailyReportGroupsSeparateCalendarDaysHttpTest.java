package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.entity.Purchase;
import com.example.repository.PurchaseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The admin dashboard daily report groups by calendar {@code purchaseDate},
 * not only by SKU. Two real checkouts of the same product on different UTC
 * days must stay on separate HTTP rows (qty/revenue not summed together).
 * Complements the DataJpa grouping fixture on main and same-day SUM tests
 * in earlier coverage PRs. Catches a regression that adds
 * {@code WHERE purchaseDate = CURRENT_DATE} or drops date from GROUP BY.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DailyReportGroupsSeparateCalendarDaysHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Test
    void dailyReport_keepsYesterdayAndTodayOnSeparateRows_sameSku() throws Exception {
        String create = "{\"name\":\"Cov32 Multi Day Sku\",\"description\":\"d\","
                + "\"price\":5.00,\"quantity\":20,\"image\":\"\"}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .with(httpBasic("admin", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(5.00));

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":3}"))
                .andExpect(status().isCreated());

        List<Purchase> mine = new ArrayList<>();
        for (Purchase purchase : purchaseRepository.findAll()) {
            if (purchase.getProduct() != null && id == purchase.getProduct().getId()) {
                mine.add(purchase);
            }
        }
        Assertions.assertEquals(2, mine.size());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);
        mine.get(0).setPurchaseDate(yesterday);
        purchaseRepository.save(mine.get(0));

        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode yesterdayRow = null;
        JsonNode todayRow = null;
        int todayIdx = -1;
        int yesterdayIdx = -1;
        int i = 0;
        for (JsonNode row : objectMapper.readTree(report.getResponse().getContentAsString())) {
            if (row.path("productId").asLong() == id) {
                String date = row.path("date").asText();
                if (yesterday.toString().equals(date)) {
                    yesterdayRow = row;
                    yesterdayIdx = i;
                } else if (today.toString().equals(date)) {
                    todayRow = row;
                    todayIdx = i;
                }
            }
            i++;
        }
        Assertions.assertNotNull(yesterdayRow, "dashboard must still list the prior UTC day's row");
        Assertions.assertNotNull(todayRow, "dashboard must list today's row for the same SKU");
        Assertions.assertEquals("Cov32 Multi Day Sku", yesterdayRow.path("productName").asText());
        Assertions.assertEquals("Cov32 Multi Day Sku", todayRow.path("productName").asText());

        int yesterdayQty = yesterdayRow.path("totalQuantity").asInt();
        int todayQty = todayRow.path("totalQuantity").asInt();
        Assertions.assertEquals(5, yesterdayQty + todayQty, "both checkouts must appear, split across days");
        Assertions.assertTrue(yesterdayQty == 2 || yesterdayQty == 3);
        Assertions.assertEquals(5 - yesterdayQty, todayQty);
        Assertions.assertEquals(0, new BigDecimal(yesterdayRow.path("totalRevenue").asText())
                .compareTo(new BigDecimal("5.00").multiply(BigDecimal.valueOf(yesterdayQty))));
        Assertions.assertEquals(0, new BigDecimal(todayRow.path("totalRevenue").asText())
                .compareTo(new BigDecimal("5.00").multiply(BigDecimal.valueOf(todayQty))));
        Assertions.assertTrue(todayIdx < yesterdayIdx, "ORDER BY date DESC must list today before yesterday");
    }
}
