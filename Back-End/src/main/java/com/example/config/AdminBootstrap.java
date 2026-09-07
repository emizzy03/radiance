package com.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.example.service.UserService;

/**
 * Seeds an initial ROLE_ADMIN user on startup when no admin exists yet, so the
 * admin page can be used out of the box. Credentials are read from environment
 * variables with safe development defaults; override them in real deployments.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Autowired
    private UserService userService;

    @Autowired
    private Environment environment;

    @Override
    public void run(String... args) {
        String username = environment.getProperty("ADMIN_USERNAME", "admin");
        String email = environment.getProperty("ADMIN_EMAIL", "admin@radiance.local");
        String password = environment.getProperty("ADMIN_PASSWORD", "admin12345");

        if (userService.existsByUsername(username)) {
            log.info("Admin bootstrap: user '{}' already exists, skipping seed.", username);
            return;
        }
        try {
            userService.createAdminUser(username, email, password);
            log.info("Admin bootstrap: created admin user '{}'.", username);
        } catch (Exception e) {
            log.warn("Admin bootstrap: could not create admin user '{}': {}", username, e.getMessage());
        }
    }
}
