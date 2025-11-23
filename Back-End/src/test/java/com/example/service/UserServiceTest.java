package com.example.service;

import com.example.entity.User;
import com.example.entity.Role;
import com.example.repository.UserRepository;
import com.example.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private Role adminRole;
    private Role userRole;

    @BeforeEach
    void setUp() {
        testUser = new User("testuser", "test@example.com", "password123");
        testUser.setId(1L);
        
        adminRole = new Role("ROLE_ADMIN");
        adminRole.setId(1L);
        
        userRole = new Role("ROLE_USER");
        userRole.setId(2L);
    }

    @Test
    void testGetUserByUsername_Found() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        
        User found = userService.getUserByUsername("testuser");
        
        assertNotNull(found);
        assertEquals("testuser", found.getUsername());
        verify(userRepository, times(1)).findByUsername("testuser");
    }

    @Test
    void testGetUserByUsername_NotFound() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());
        
        User found = userService.getUserByUsername("nonexistent");
        
        assertNull(found);
        verify(userRepository, times(1)).findByUsername("nonexistent");
    }

    @Test
    void testFindByUsername() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        
        Optional<User> found = userService.findByUsername("testuser");
        
        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().getUsername());
    }

    @Test
    void testGetAllUsers() {
        List<User> users = Arrays.asList(testUser, new User("user2", "user2@example.com", "pass"));
        when(userRepository.findAll()).thenReturn(users);
        
        List<User> result = userService.getAllUsers();
        
        assertEquals(2, result.size());
        verify(userRepository, times(1)).findAll();
    }

    @Test
    void testCreateUser() {
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        User created = userService.createUser(testUser);
        
        assertNotNull(created);
        verify(passwordEncoder, times(1)).encode("password123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testCreateAdminUser_Success() {
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("adminpass")).thenReturn("encodedAdminPass");
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(adminRole);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        User admin = userService.createAdminUser("admin", "admin@example.com", "adminpass");
        
        assertNotNull(admin);
        verify(userRepository, times(1)).existsByUsername("admin");
        verify(userRepository, times(1)).existsByEmail("admin@example.com");
        verify(roleRepository, times(1)).findByName("ROLE_ADMIN");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testCreateAdminUser_UsernameExists() {
        when(userRepository.existsByUsername("admin")).thenReturn(true);
        
        assertThrows(IllegalArgumentException.class, () -> {
            userService.createAdminUser("admin", "admin@example.com", "adminpass");
        });
        
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testCreateAdminUser_EmailExists() {
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);
        
        assertThrows(IllegalArgumentException.class, () -> {
            userService.createAdminUser("admin", "admin@example.com", "adminpass");
        });
        
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testCreateAdminUser_RoleNotFound_AutoCreatesRole() {
        // When ROLE_ADMIN doesn't exist, the service should auto-create it
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(null);
        when(passwordEncoder.encode("adminpass")).thenReturn("encodedAdminPass");
        when(roleRepository.save(any(Role.class))).thenReturn(adminRole);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        User admin = userService.createAdminUser("admin", "admin@example.com", "adminpass");
        
        assertNotNull(admin);
        verify(roleRepository, times(1)).findByName("ROLE_ADMIN");
        verify(roleRepository, times(1)).save(any(Role.class)); // Verify role was auto-created
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testUpdatePassword_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPassword", testUser.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("newPassword123")).thenReturn("encodedNewPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        User updated = userService.updatePassword(1L, "oldPassword", "newPassword123");
        
        assertNotNull(updated);
        verify(passwordEncoder, times(1)).encode("newPassword123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testUpdatePassword_UserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        
        assertThrows(IllegalArgumentException.class, () -> {
            userService.updatePassword(1L, "oldPassword", "newPassword");
        });
    }

    @Test
    void testUpdatePassword_IncorrectCurrentPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", testUser.getPassword())).thenReturn(false);
        
        assertThrows(IllegalArgumentException.class, () -> {
            userService.updatePassword(1L, "wrongPassword", "newPassword");
        });
    }

    @Test
    void testUpdatePassword_NewPasswordTooShort() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPassword", testUser.getPassword())).thenReturn(true);
        
        assertThrows(IllegalArgumentException.class, () -> {
            userService.updatePassword(1L, "oldPassword", "short");
        });
    }

    @Test
    void testFindByEmail_Found() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        
        User found = userService.findByEmail("test@example.com");
        
        assertNotNull(found);
        assertEquals("test@example.com", found.getEmail());
    }

    @Test
    void testFindByEmail_NotFound() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());
        
        User found = userService.findByEmail("notfound@example.com");
        
        assertNull(found);
    }

    @Test
    void testExistsByUsername() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);
        
        boolean exists = userService.existsByUsername("testuser");
        
        assertTrue(exists);
    }

    @Test
    void testExistsByEmail() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);
        
        boolean exists = userService.existsByEmail("test@example.com");
        
        assertTrue(exists);
    }

    @Test
    void testGetUserById_Found() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        
        User found = userService.getUserById(1L);
        
        assertNotNull(found);
        assertEquals(1L, found.getId());
    }

    @Test
    void testGetUserById_NotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        
        User found = userService.getUserById(999L);
        
        assertNull(found);
    }

    @Test
    void testAssignRoleToUser_Success() {
        testUser.setRoles(new java.util.HashSet<>());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(adminRole);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        userService.assignRoleToUser(1L, "ROLE_ADMIN");
        
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testAssignRoleToUser_UserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        
        assertThrows(IllegalArgumentException.class, () -> {
            userService.assignRoleToUser(999L, "ROLE_ADMIN");
        });
    }

    @Test
    void testAssignRoleToUser_RoleNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName("ROLE_INVALID")).thenReturn(null);
        
        assertThrows(IllegalArgumentException.class, () -> {
            userService.assignRoleToUser(1L, "ROLE_INVALID");
        });
    }

    @Test
    void testRemoveRoleFromUser_Success() {
        testUser.setRoles(new java.util.HashSet<>(Collections.singletonList(adminRole)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(adminRole);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        userService.removeRoleFromUser(1L, "ROLE_ADMIN");
        
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testUpdateUser_Success() {
        User updateDetails = new User("newusername", "newemail@example.com", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        User updated = userService.updateUser(1L, updateDetails);
        
        assertNotNull(updated);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testUpdateUser_WithPassword_ThrowsException() {
        User updateDetails = new User("newusername", "newemail@example.com", "newpassword");
        
        assertThrows(IllegalArgumentException.class, () -> {
            userService.updateUser(1L, updateDetails);
        });
    }

    @Test
    void testUpdateUser_NotFound() {
        User updateDetails = new User("newusername", "newemail@example.com", null);
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        
        User updated = userService.updateUser(999L, updateDetails);
        
        assertNull(updated);
    }

    @Test
    void testDeleteUser_Success() {
        when(userRepository.existsById(1L)).thenReturn(true);
        doNothing().when(userRepository).deleteById(1L);
        
        boolean deleted = userService.deleteUser(1L);
        
        assertTrue(deleted);
        verify(userRepository, times(1)).deleteById(1L);
    }

    @Test
    void testDeleteUser_NotFound() {
        when(userRepository.existsById(999L)).thenReturn(false);
        
        boolean deleted = userService.deleteUser(999L);
        
        assertFalse(deleted);
        verify(userRepository, never()).deleteById(anyLong());
    }
}
