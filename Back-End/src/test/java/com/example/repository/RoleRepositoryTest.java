package com.example.repository;

import com.example.entity.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class RoleRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    private Role adminRole;
    private Role userRole;

    @BeforeEach
    void setUp() {
        adminRole = new Role("ROLE_ADMIN");
        userRole = new Role("ROLE_USER");
        
        entityManager.persist(adminRole);
        entityManager.persist(userRole);
        entityManager.flush();
    }

    @Test
    void testFindByName_Found() {
        Role found = roleRepository.findByName("ROLE_ADMIN");
        
        assertNotNull(found);
        assertEquals("ROLE_ADMIN", found.getName());
    }

    @Test
    void testFindByName_NotFound() {
        Role found = roleRepository.findByName("ROLE_MODERATOR");
        
        assertNull(found);
    }

    @Test
    void testSaveRole() {
        Role newRole = new Role("ROLE_MODERATOR");
        Role saved = roleRepository.save(newRole);
        
        assertNotNull(saved.getId());
        assertEquals("ROLE_MODERATOR", saved.getName());
    }

    @Test
    void testUpdateRole() {
        adminRole.setName("ROLE_SUPER_ADMIN");
        Role updated = roleRepository.save(adminRole);
        
        assertEquals("ROLE_SUPER_ADMIN", updated.getName());
    }

    @Test
    void testDeleteRole() {
        Long roleId = adminRole.getId();
        roleRepository.delete(adminRole);
        
        var found = roleRepository.findById(roleId);
        assertFalse(found.isPresent());
    }

    @Test
    void testFindAll() {
        var roles = roleRepository.findAll();
        
        assertTrue(roles.size() >= 2);
    }

    @Test
    void testFindById() {
        var found = roleRepository.findById(userRole.getId());
        
        assertTrue(found.isPresent());
        assertEquals("ROLE_USER", found.get().getName());
    }
}
