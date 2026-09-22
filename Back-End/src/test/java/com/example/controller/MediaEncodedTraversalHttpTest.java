package com.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Public {@code GET /media/{filename}} must not serve files outside the uploads
 * root when the filename is URL-encoded ({@code %2e%2e} / encoded slashes).
 * Literal {@code evil..png} 404 is covered elsewhere; encoded traversal is the
 * remaining HTTP attack spelling.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MediaEncodedTraversalHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private void assertNotASuccessfulFile(MvcResult result) throws Exception {
        int status = result.getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(status == 400 || status == 404,
                "encoded traversal must be rejected, got " + status);
        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("spring:") || body.contains("groupId") || body.contains("ADMIN_PASSWORD"),
                "response must not leak repository or config files");
    }

    @Test
    void encodedDotDotFilename_isRejected() throws Exception {
        assertNotASuccessfulFile(mockMvc.perform(get("/media/%2e%2e")).andReturn());
    }

    @Test
    void encodedDotDotSlashFilename_isRejected() throws Exception {
        assertNotASuccessfulFile(mockMvc.perform(get("/media/%2e%2e%2fpom.xml")).andReturn());
        assertNotASuccessfulFile(mockMvc.perform(get("/media/..%2f..%2fetc%2fpasswd")).andReturn());
    }
}
