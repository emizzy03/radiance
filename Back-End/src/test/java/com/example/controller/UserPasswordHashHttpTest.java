package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.entity.User;
import com.example.repository.UserRepository;

/**
 * Registration must persist a BCrypt hash, not the plaintext password. Unit
 * tests mock {@code PasswordEncoder}, so they cannot catch a NoOp encoder
 * bean swap that would store credentials in the clear.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserPasswordHashHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registration_persistsBcryptHashAndPlaintextStillAuthenticates() throws Exception {
        String username = "hashuser20";
        String plaintext = "password123";
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"hashuser20@x.com\","
                                + "\"password\":\"" + plaintext + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist());

        User stored = userRepository.findByUsername(username).orElseThrow();
        assertThat(stored.getPassword()).isNotEqualTo(plaintext);
        assertThat(stored.getPassword()).startsWith("$2");
        assertThat(stored.getPassword().length()).isGreaterThan(20);

        mockMvc.perform(get("/api/auth/me").with(httpBasic(username, plaintext)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        // The stored hash must not itself be usable as the HTTP Basic password.
        mockMvc.perform(get("/api/auth/me").with(httpBasic(username, stored.getPassword())))
                .andExpect(status().isUnauthorized());
    }
}
