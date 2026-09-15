package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

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
 * Checkout snapshots {@code purchaseDate} in UTC and the admin daily-report
 * table reads {@code date} as an ISO-8601 string. Complements draft coverage of
 * unitPrice / product id on the 201 body and report aggregation math, which
 * never locked the date JSON contract the admin UI interpolates.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PurchaseDateIsoContractHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long createProduct(String name) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"description\":\"d\",\"price\":7.25,"
                + "\"quantity\":6,\"image\":\"/media/x.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void checkout_201IncludesUtcPurchaseDateAndCreatedAt() throws Exception {
        long id = createProduct("Cov19DateLamp");
        LocalDate utcToday = LocalDate.now(ZoneOffset.UTC);

        MvcResult result = mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(1))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode purchaseDate = body.get("purchaseDate");
        JsonNode createdAt = body.get("createdAt");

        assertThat(purchaseDate).isNotNull();
        assertThat(purchaseDate.isTextual()).isTrue();
        assertThat(purchaseDate.asText()).isEqualTo(utcToday.toString());

        assertThat(createdAt).isNotNull();
        assertThat(createdAt.isTextual()).isTrue();
        Instant parsed = Instant.parse(createdAt.asText());
        assertThat(parsed).isBeforeOrEqualTo(Instant.now());
        assertThat(parsed).isAfter(Instant.now().minusSeconds(60));
    }

    @Test
    void dailyReport_dateIsIso8601UtcTodayForNewCheckout() throws Exception {
        long id = createProduct("Cov19ReportDateMug");
        LocalDate utcToday = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated());

        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rows = objectMapper.readTree(report.getResponse().getContentAsString());
        JsonNode row = null;
        for (JsonNode candidate : rows) {
            if (candidate.path("productId").asLong() == id) {
                row = candidate;
                break;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("date").isTextual()).isTrue();
        assertThat(row.get("date").asText()).isEqualTo(utcToday.toString());
        assertThat(row.get("productName").asText()).isEqualTo("Cov19ReportDateMug");
        assertThat(row.get("totalQuantity").asLong()).isEqualTo(2L);
    }
}
