package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
 * Checkout is public and CSRF is off (stateless Basic). A cross-origin HTML
 * form posts {@code application/x-www-form-urlencoded} or multipart, not
 * JSON. Those bodies must not return 2xx or decrement stock. Truncated JSON
 * is already covered; this is the browser-form content-type path.
 *
 * <p>Unsupported media types currently map through the generic handler as
 * 500. This test does not lock that status in.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutNonJsonBodyDoesNotSellHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long createProduct(String name, int quantity) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"description\":\"d\",\"price\":3.00,\"quantity\":"
                + quantity + ",\"image\":\"/media/x.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private boolean reportContains(long productId) throws Exception {
        MvcResult report = mockMvc.perform(get("/api/purchases/daily-report")
                        .with(httpBasic("admin", "admin12345")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(report.getResponse().getContentAsString());
        for (JsonNode row : rows) {
            if (row.path("productId").asLong() == productId) {
                return true;
            }
        }
        return false;
    }

    @Test
    void formUrlencodedCheckout_doesNotDecrementStock() throws Exception {
        long id = createProduct("Cov27FormBuy", 8);

        MvcResult result = mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("productId=" + id + "&quantity=2"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                result.getResponse().getStatus() >= 400,
                "form checkout must not succeed; got " + result.getResponse().getStatus());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(8));
        org.junit.jupiter.api.Assertions.assertFalse(reportContains(id),
                "form checkout must not create a report row");
    }

    @Test
    void textPlainJsonCheckout_doesNotDecrementStock() throws Exception {
        long id = createProduct("Cov27PlainBuy", 8);

        MvcResult result = mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                result.getResponse().getStatus() >= 400,
                "text/plain checkout must not succeed; got " + result.getResponse().getStatus());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(8));
        org.junit.jupiter.api.Assertions.assertFalse(reportContains(id));
    }

    @Test
    void multipartCheckout_doesNotDecrementStock() throws Exception {
        long id = createProduct("Cov27MultiBuy", 8);

        MvcResult result = mockMvc.perform(multipart("/api/purchases")
                        .param("productId", Long.toString(id))
                        .param("quantity", "2"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                result.getResponse().getStatus() >= 400,
                "multipart checkout must not succeed; got " + result.getResponse().getStatus());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(8));
        org.junit.jupiter.api.Assertions.assertFalse(reportContains(id));
    }
}
