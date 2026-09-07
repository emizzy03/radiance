package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.entity.User;
import com.example.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
    }

    @Test
    void createUser_acceptsPasswordAndDoesNotReturnIt() throws Exception {
        // Regression test: password used to be dropped during deserialization
        // because getPassword() was @JsonIgnore, causing registration to fail.
        when(userService.findByUsername("charlie")).thenReturn(Optional.empty());
        when(userService.findByEmail("charlie@example.com")).thenReturn(null);
        when(userService.createUser(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });

        String body = "{\"username\":\"charlie\",\"email\":\"charlie@example.com\",\"password\":\"supersecret\"}";

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("charlie"))
                .andExpect(jsonPath("$.email").value("charlie@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        // Verify the password actually reached the service (deserialization worked).
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userService).createUser(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("supersecret");
    }

    @Test
    void createUser_rejectsBlankPassword() throws Exception {
        String body = "{\"username\":\"charlie\",\"email\":\"charlie@example.com\",\"password\":\"\"}";

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_rejectsShortPassword() throws Exception {
        String body = "{\"username\":\"charlie\",\"email\":\"charlie@example.com\",\"password\":\"short\"}";

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
