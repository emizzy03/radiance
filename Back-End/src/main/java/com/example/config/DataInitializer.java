package com.example.config;

import com.example.entity.Role;
import com.example.repository.RoleRepository;
import com.example.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes default roles and admin user on application startup
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserService userService;

    @Override
    public void run(String... args) throws Exception {
        initializeRoles();
        initializeAdminUser();
    }

    private void initializeRoles() {
        // Create ROLE_USER if it doesn't exist
        if (roleRepository.findByName("ROLE_USER") == null) {
            Role userRole = new Role("ROLE_USER");
            roleRepository.save(userRole);
            System.out.println("✅ Created ROLE_USER");
        }

        // Create ROLE_ADMIN if it doesn't exist
        if (roleRepository.findByName("ROLE_ADMIN") == null) {
            Role adminRole = new Role("ROLE_ADMIN");
            roleRepository.save(adminRole);
            System.out.println("✅ Created ROLE_ADMIN");
        }
    }

    private void initializeAdminUser() {
        // Check if admin user already exists
        if (!userService.existsByUsername("admin")) {
            try {
                // Create admin user with default credentials
                String adminUsername = "admin";
                String adminEmail = "admin@radiance.com";
                String adminPassword = "admin123"; // Change this in production!

                userService.createAdminUser(adminUsername, adminEmail, adminPassword);
                System.out.println("✅ Created admin user:");
                System.out.println("   Username: " + adminUsername);
                System.out.println("   Email: " + adminEmail);
                System.out.println("   Password: admin123 (CHANGE THIS IN PRODUCTION!)");
            } catch (Exception e) {
                System.err.println("❌ Failed to create admin user: " + e.getMessage());
            }
        } else {
            System.out.println("ℹ️  Admin user already exists");
        }
    }
}

