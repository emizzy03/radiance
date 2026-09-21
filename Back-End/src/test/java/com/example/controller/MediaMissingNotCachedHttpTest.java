package com.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Successful media GETs advertise a week-long public cache. A 404 for a
 * missing (or not-yet-uploaded) filename must not reuse that cache policy, or
 * browsers/CDNs would keep a broken storefront image after the file later
 * appears (or keep hiding a file that is added under that name).
 */
@SpringBootTest
@AutoConfigureMockMvc
class MediaMissingNotCachedHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingMedia_isNotFoundWithoutWeekLongPublicCache() throws Exception {
        MvcResult result = mockMvc.perform(get("/media/cov25-does-not-exist.png"))
                .andExpect(status().isNotFound())
                .andReturn();
        String cacheControl = result.getResponse().getHeader("Cache-Control");
        org.junit.jupiter.api.Assertions.assertFalse(
                cacheControl != null && cacheControl.contains("max-age=604800"),
                "missing media must not advertise the 7-day public cache used for hits; got: "
                        + cacheControl);
    }
}
