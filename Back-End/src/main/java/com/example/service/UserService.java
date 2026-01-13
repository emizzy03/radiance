package com.example.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.entity.Role;
import com.example.entity.User;
import com.example.repository.RoleRepository;
import com.example.repository.UserRepository;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User getUserByUsername(String username) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isPresent()) {
            return userOptional.get();
        }
        return null;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User createUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public User createAdminUser(String username, String email, String password) {
        // Check if username or email already exists
        if (existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        // Create new user with admin role
        User adminUser = new User(username, email, passwordEncoder.encode(password));
        Role adminRole = roleRepository.findByName("ROLE_ADMIN");
        if (adminRole == null) {
            // Auto-create ROLE_ADMIN if it doesn't exist
            adminRole = new Role("ROLE_ADMIN");
            adminRole = roleRepository.save(adminRole);
        }
        if (adminUser.getRoles() == null) {
            adminUser.setRoles(new java.util.HashSet<>());
        }
        adminUser.getRoles().add(adminRole);
        return userRepository.save(adminUser);
    }

    public User updatePassword(Long id, String currentPassword, String newPassword) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters long");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    public User findByEmail(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            return userOptional.get();
        }
        return null;
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public void assignRoleToUser(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        Role role = roleRepository.findByName(roleName);
        if (role == null) {
            throw new IllegalArgumentException("Role not found with name: " + roleName);
        }
        if (user.getRoles() == null) {
            user.setRoles(new java.util.HashSet<>());
        }
        user.getRoles().add(role);
        userRepository.save(user);
    }

    public void removeRoleFromUser(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        Role role = roleRepository.findByName(roleName);
        if (role == null) {
            throw new IllegalArgumentException("Role not found with name: " + roleName);
        }
        if (user.getRoles() == null) {
            return; // No roles to remove
        }
        user.getRoles().remove(role);
        userRepository.save(user);
    }

    public User updateUser(Long id, User userDetails) {
        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password updates are not allowed in updateUser. Use updatePassword instead.");
        }
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            if (userDetails.getUsername() != null) {
                user.setUsername(userDetails.getUsername());
            }
            if (userDetails.getEmail() != null) {
                user.setEmail(userDetails.getEmail());
            }
            return userRepository.save(user);
        }
        return null;
    }

    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
