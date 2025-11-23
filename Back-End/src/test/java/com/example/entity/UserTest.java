package com.example.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.HashSet;

class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(user);
        assertNull(user.getId());
        assertNull(user.getUsername());
        assertNull(user.getEmail());
        assertNull(user.getPassword());
    }

    @Test
    void testParameterizedConstructor() {
        User testUser = new User("testuser", "test@example.com", "password123");
        
        assertEquals("testuser", testUser.getUsername());
        assertEquals("test@example.com", testUser.getEmail());
        assertEquals("password123", testUser.getPassword());
        assertNull(testUser.getId());
    }

    @Test
    void testSettersAndGetters() {
        user.setId(1L);
        user.setUsername("john_doe");
        user.setEmail("john@example.com");
        user.setPassword("securePassword");

        assertEquals(1L, user.getId());
        assertEquals("john_doe", user.getUsername());
        assertEquals("john@example.com", user.getEmail());
        assertEquals("securePassword", user.getPassword());
    }

    @Test
    void testRolesManagement() {
        Role adminRole = new Role("ROLE_ADMIN");
        Role userRole = new Role("ROLE_USER");
        
        Collection<Role> roles = new HashSet<>();
        roles.add(adminRole);
        roles.add(userRole);
        
        user.setRoles(roles);
        
        assertNotNull(user.getRoles());
        assertEquals(2, user.getRoles().size());
        assertTrue(user.getRoles().contains(adminRole));
        assertTrue(user.getRoles().contains(userRole));
    }

    @Test
    void testRolesCanBeNull() {
        user.setRoles(null);
        assertNull(user.getRoles());
    }

    @Test
    void testRolesCanBeEmpty() {
        Collection<Role> emptyRoles = new HashSet<>();
        user.setRoles(emptyRoles);
        
        assertNotNull(user.getRoles());
        assertTrue(user.getRoles().isEmpty());
    }

    @Test
    void testEmailUpdate() {
        user.setEmail("old@example.com");
        assertEquals("old@example.com", user.getEmail());
        
        user.setEmail("new@example.com");
        assertEquals("new@example.com", user.getEmail());
    }

    @Test
    void testUsernameUpdate() {
        user.setUsername("oldusername");
        assertEquals("oldusername", user.getUsername());
        
        user.setUsername("newusername");
        assertEquals("newusername", user.getUsername());
    }

    @Test
    void testPasswordUpdate() {
        user.setPassword("oldPassword");
        assertEquals("oldPassword", user.getPassword());
        
        user.setPassword("newPassword");
        assertEquals("newPassword", user.getPassword());
    }
}
