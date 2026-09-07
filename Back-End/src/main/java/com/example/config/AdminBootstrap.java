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
 * variables; the well-known development password is only used when the
 * {@code dev} or {@code test} profile is explicitly in effect. Any other
 * deployment must supply {@code ADMIN_PASSWORD} or no admin is seeded.
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
                log.warn("Admin bootstrap: ADMIN_PASSWORD is not set and neither the 'dev' nor the 'test' "
                        + "profile is active; skipping admin seed. Set ADMIN_PASSWORD to bootstrap an admin "
                        + "in this environment.");
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
     * Dev mode requires the {@code dev} or {@code test} profile to be explicitly
     * in effect. Everything else, including the empty profile set Spring Boot
     * uses for a plain {@code java -jar} or container start, is treated as a real
     * deployment: that state is indistinguishable from a production launch that
     * simply forgot to set {@code spring.profiles.active}.
     *
     * <p>When no profile is active, Spring applies {@code spring.profiles.default}
     * (itself defaulting to {@code default}), so that set is consulted the same
     * way {@code @Profile} resolves it.
     */
    private boolean isDevProfile() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        for (String profile : profiles) {
            if ("dev".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
