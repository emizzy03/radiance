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

    /** Insecure default only ever used when explicitly running in dev/test. */
    private static final String DEV_DEFAULT_PASSWORD = "admin12345";

    @Override
    public void run(String... args) {
        String username = environment.getProperty("ADMIN_USERNAME", "admin");
        String email = environment.getProperty("ADMIN_EMAIL", "admin@radiance.local");
        String password = environment.getProperty("ADMIN_PASSWORD");

        if (userService.existsByUsername(username)) {
            log.info("Admin bootstrap: user '{}' already exists, skipping seed.", username);
            return;
        }

        // Never fall back to a hard-coded default password outside development.
        // In non-dev profiles ADMIN_PASSWORD must be provided explicitly; if it
        // is missing we skip seeding rather than create a well-known account.
        if (password == null || password.isBlank()) {
            if (!isDevProfile()) {
                log.warn("Admin bootstrap: ADMIN_PASSWORD is not set and no dev profile is active; "
                        + "skipping admin seed. Set ADMIN_PASSWORD to bootstrap an admin in this environment.");
                return;
            }
            password = DEV_DEFAULT_PASSWORD;
            log.warn("Admin bootstrap: ADMIN_PASSWORD not set; using the INSECURE development default "
                    + "for user '{}'. Set ADMIN_PASSWORD for any non-development deployment.", username);
        }

        try {
            userService.createAdminUser(username, email, password);
            log.info("Admin bootstrap: created admin user '{}'.", username);
        } catch (Exception e) {
            log.warn("Admin bootstrap: could not create admin user '{}': {}", username, e.getMessage());
        }
    }

    /**
     * Dev mode is when the {@code dev} or {@code test} profile is active, or when
     * no profile is active at all (a plain local run). Any explicit non-dev
     * profile (e.g. {@code prod}) is treated as a real deployment.
     */
    private boolean isDevProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return true;
        }
        for (String profile : active) {
            if ("dev".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
