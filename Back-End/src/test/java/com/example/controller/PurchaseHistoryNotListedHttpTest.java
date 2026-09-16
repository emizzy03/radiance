package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * There is no collection GET for raw purchase rows. Checkout history must not
 * be dumpable via {@code GET /api/purchases}, and the daily-report path must
 * not accept writes. A future mapping that listed every {@code Purchase}
 * would leak customer order data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PurchaseHistoryNotListedHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getPurchasesCollection_anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/purchases"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPurchasesCollection_nonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/purchases").with(user("shopper20").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPurchasesCollection_adminDoesNotReceivePurchaseDump() throws Exception {
        // No GET collection mapping exists (only POST checkout and GET
        // /daily-report). Do not assert a specific 404/405: the generic
        // exception handler currently masks method-not-supported as 500.
        // The invariant is that this path never returns 200 with purchase rows.
        MvcResult result = mockMvc.perform(get("/api/purchases").with(httpBasic("admin", "admin12345")))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("unitPrice");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("purchaseDate");
    }

    @Test
    void postDailyReport_anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/purchases/daily-report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
