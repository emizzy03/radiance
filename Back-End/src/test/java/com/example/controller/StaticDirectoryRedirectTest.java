package com.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code /admin} and {@code /shop} without a trailing slash must redirect into
 * the directory URLs that forward to the SPAs. A missed mapping 401s the pages
 * behind HTTP Basic.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaticDirectoryRedirectTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminWithoutSlash_redirectsToDirectory() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/"));
    }

    @Test
    void shopWithoutSlash_redirectsToDirectory() throws Exception {
        mockMvc.perform(get("/shop"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/shop/"));
    }
}
