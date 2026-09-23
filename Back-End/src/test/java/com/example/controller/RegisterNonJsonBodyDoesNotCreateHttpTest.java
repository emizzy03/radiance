package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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
 * Registration is public and CSRF is off. A cross-site HTML form posts
 * urlencoded fields, not JSON. That must not create a loginable account.
 * JSON registration (including mass-assignment and duplicate identity) is
 * already covered; this is the browser-form content-type path.
 *
 * <p>Unsupported media types currently map as a generic 500. This test does
 * not lock that status in.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegisterNonJsonBodyDoesNotCreateHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void formUrlencodedRegistration_doesNotCreateLoginableAccount() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("username=cov27formuser&email=cov27formuser%40x.com&password=formpass1"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                result.getResponse().getStatus() >= 400,
                "form registration must not succeed; got " + result.getResponse().getStatus());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov27formuser", "formpass1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void textPlainJsonRegistration_doesNotCreateLoginableAccount() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"username\":\"cov27plainuser\",\"email\":\"cov27plainuser@x.com\","
                                + "\"password\":\"plainpass\"}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                result.getResponse().getStatus() >= 400,
                "text/plain registration must not succeed; got " + result.getResponse().getStatus());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("cov27plainuser", "plainpass")))
                .andExpect(status().isUnauthorized());
    }
}
