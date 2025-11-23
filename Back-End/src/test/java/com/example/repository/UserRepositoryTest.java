package com.example.repository;

import com.example.entity.User;
import com.example.entity.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("testuser", "test@example.com", "password123");
        entityManager.persist(testUser);
        entityManager.flush();
    }

    @Test
    void testFindByUsername_Found() {
        Optional<User> found = userRepository.findByUsername("testuser");
        
        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().getUsername());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    void testFindByUsername_NotFound() {
        Optional<User> found = userRepository.findByUsername("nonexistent");
        
        assertFalse(found.isPresent());
    }

    @Test
    void testFindByEmail_Found() {
        Optional<User> found = userRepository.findByEmail("test@example.com");
        
        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().getUsername());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    void testFindByEmail_NotFound() {
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");
        
        assertFalse(found.isPresent());
    }

    @Test
    void testExistsByUsername_True() {
        boolean exists = userRepository.existsByUsername("testuser");
        
        assertTrue(exists);
    }

    @Test
    void testExistsByUsername_False() {
        boolean exists = userRepository.existsByUsername("nonexistent");
        
        assertFalse(exists);
    }

    @Test
    void testExistsByEmail_True() {
        boolean exists = userRepository.existsByEmail("test@example.com");
        
        assertTrue(exists);
    }

    @Test
    void testExistsByEmail_False() {
        boolean exists = userRepository.existsByEmail("nonexistent@example.com");
        
        assertFalse(exists);
    }

    @Test
    void testSaveUser() {
        User newUser = new User("newuser", "new@example.com", "newpassword");
        User saved = userRepository.save(newUser);
        
        assertNotNull(saved.getId());
        assertEquals("newuser", saved.getUsername());
        assertEquals("new@example.com", saved.getEmail());
    }

    @Test
    void testUpdateUser() {
        testUser.setEmail("updated@example.com");
        User updated = userRepository.save(testUser);
        
        assertEquals("updated@example.com", updated.getEmail());
        assertEquals("testuser", updated.getUsername());
    }

    @Test
    void testDeleteUser() {
        Long userId = testUser.getId();
        userRepository.delete(testUser);
        
        Optional<User> found = userRepository.findById(userId);
        assertFalse(found.isPresent());
    }

    @Test
    void testFindAll() {
        User user2 = new User("user2", "user2@example.com", "pass2");
        User user3 = new User("user3", "user3@example.com", "pass3");
        
        entityManager.persist(user2);
        entityManager.persist(user3);
        entityManager.flush();
        
        var users = userRepository.findAll();
        
        assertTrue(users.size() >= 3);
    }

    @Test
    void testFindByUsername_CaseSensitive() {
        Optional<User> found = userRepository.findByUsername("TESTUSER");
        
        // Username search should be case-sensitive
        assertFalse(found.isPresent());
    }

    @Test
    void testUniqueConstraints() {
        User duplicateUsername = new User("testuser", "different@example.com", "pass");
        
        assertThrows(Exception.class, () -> {
            entityManager.persist(duplicateUsername);
            entityManager.flush();
        });
    }
}
