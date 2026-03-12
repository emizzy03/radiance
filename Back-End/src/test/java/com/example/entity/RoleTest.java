package com.example.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    private Role role;

    @BeforeEach
    void setUp() {
        role = new Role();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(role);
        assertNull(role.getId());
        assertNull(role.getName());
    }

    @Test
    void testParameterizedConstructor() {
        Role adminRole = new Role("ROLE_ADMIN");
        
        assertEquals("ROLE_ADMIN", adminRole.getName());
        assertNull(adminRole.getId());
    }

    @Test
    void testSettersAndGetters() {
        role.setId(1L);
        role.setName("ROLE_USER");

        assertEquals(1L, role.getId());
        assertEquals("ROLE_USER", role.getName());
    }

    @Test
    void testRoleNameUpdate() {
        role.setName("ROLE_ADMIN");
        assertEquals("ROLE_ADMIN", role.getName());
        
        role.setName("ROLE_MODERATOR");
        assertEquals("ROLE_MODERATOR", role.getName());
    }

    @Test
    void testNullRoleName() {
        role.setName(null);
        assertNull(role.getName());
    }

    @Test
    void testEmptyRoleName() {
        role.setName("");
        assertEquals("", role.getName());
    }

    @Test
    void testMultipleRoleInstances() {
        Role role1 = new Role("ROLE_ADMIN");
        Role role2 = new Role("ROLE_USER");
        Role role3 = new Role("ROLE_MODERATOR");
        
        assertEquals("ROLE_ADMIN", role1.getName());
        assertEquals("ROLE_USER", role2.getName());
        assertEquals("ROLE_MODERATOR", role3.getName());
        
        assertNotSame(role1, role2);
        assertNotSame(role2, role3);
    }
}
