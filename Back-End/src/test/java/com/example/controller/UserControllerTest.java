package com.example.controller;

import com.example.entity.User;
import com.example.entity.ErrorResponse;
import com.example.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.example.config.TestSecurityConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("testuser", "test@example.com", "password123");
        testUser.setId(1L);
    }

    @Test
    void testGetAllUsers_Success() throws Exception {
        List<User> users = Arrays.asList(testUser, new User("user2", "user2@example.com", "pass"));
        when(userService.getAllUsers()).thenReturn(users);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("testuser"));

        verify(userService, times(1)).getAllUsers();
    }

    @Test
    void testGetAllUsers_NoContent() throws Exception {
        when(userService.getAllUsers()).thenReturn(Arrays.asList());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isNoContent());

        verify(userService, times(1)).getAllUsers();
    }

    @Test
    void testGetUserByUsername_Found() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        mockMvc.perform(get("/api/users/username/testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(userService, times(1)).findByUsername("testuser");
    }

    @Test
    void testGetUserByUsername_NotFound() throws Exception {
        when(userService.findByUsername("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/username/nonexistent"))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).findByUsername("nonexistent");
    }

    @Test
    void testCreateUser_Success() throws Exception {
        when(userService.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userService.findByEmail(anyString())).thenReturn(null);
        when(userService.createUser(any(User.class))).thenReturn(testUser);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("testuser"));

        verify(userService, times(1)).createUser(any(User.class));
    }

    @Test
    void testCreateUser_UsernameExists() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists"));

        verify(userService, never()).createUser(any(User.class));
    }

    @Test
    void testCreateUser_EmailExists() throws Exception {
        when(userService.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userService.findByEmail("test@example.com")).thenReturn(testUser);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists"));

        verify(userService, never()).createUser(any(User.class));
    }

    @Test
    void testUpdateUser_Success() throws Exception {
        User updateDetails = new User("updateduser", "updated@example.com", null);
        when(userService.updateUser(eq(1L), any(User.class))).thenReturn(testUser);

        mockMvc.perform(patch("/api/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").exists());

        verify(userService, times(1)).updateUser(eq(1L), any(User.class));
    }

    @Test
    void testUpdateUser_NotFound() throws Exception {
        User updateDetails = new User("updateduser", "updated@example.com", null);
        when(userService.updateUser(eq(999L), any(User.class))).thenReturn(null);

        mockMvc.perform(patch("/api/users/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDetails)))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).updateUser(eq(999L), any(User.class));
    }

    @Test
    void testUpdateUser_WithPassword_BadRequest() throws Exception {
        User updateDetails = new User("updateduser", "updated@example.com", "newpassword");
        when(userService.updateUser(eq(1L), any(User.class)))
                .thenThrow(new IllegalArgumentException("Password updates are not allowed in updateUser"));

        mockMvc.perform(patch("/api/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDetails)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDeleteUser_Success() throws Exception {
        when(userService.deleteUser(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isOk());

        verify(userService, times(1)).deleteUser(1L);
    }

    @Test
    void testDeleteUser_NotFound() throws Exception {
        when(userService.deleteUser(999L)).thenReturn(false);

        mockMvc.perform(delete("/api/users/999"))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).deleteUser(999L);
    }

    @Test
    void testAssignRoleToUser_Success() throws Exception {
        doNothing().when(userService).assignRoleToUser(1L, "ROLE_ADMIN");

        mockMvc.perform(post("/api/users/1/roles/ROLE_ADMIN"))
                .andExpect(status().isOk());

        verify(userService, times(1)).assignRoleToUser(1L, "ROLE_ADMIN");
    }

    @Test
    void testAssignRoleToUser_NotFound() throws Exception {
        doThrow(new IllegalArgumentException("User not found"))
                .when(userService).assignRoleToUser(999L, "ROLE_ADMIN");

        mockMvc.perform(post("/api/users/999/roles/ROLE_ADMIN"))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).assignRoleToUser(999L, "ROLE_ADMIN");
    }

    @Test
    void testRemoveRoleFromUser_Success() throws Exception {
        doNothing().when(userService).removeRoleFromUser(1L, "ROLE_USER");

        mockMvc.perform(delete("/api/users/1/roles/ROLE_USER"))
                .andExpect(status().isOk());

        verify(userService, times(1)).removeRoleFromUser(1L, "ROLE_USER");
    }

    @Test
    void testRemoveRoleFromUser_NotFound() throws Exception {
        doThrow(new IllegalArgumentException("User not found"))
                .when(userService).removeRoleFromUser(999L, "ROLE_USER");

        mockMvc.perform(delete("/api/users/999/roles/ROLE_USER"))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).removeRoleFromUser(999L, "ROLE_USER");
    }
}
