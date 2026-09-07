package com.example.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import com.example.repository.UserRepository;

/**
 * Full-context regression test for the privilege-escalation issue where an
 * empty active-profile set — Spring Boot's default for a plain {@code java -jar}
 * or container start with no {@code spring.profiles.active} — was treated as
 * development and seeded {@code admin} with a hard-coded password.
 *
 * <p>Runs with the profiles cleared (the shared test configuration activates
 * {@code test}) and on its own in-memory database so the seeded admin of the
 * other integration tests cannot mask the result.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=",
        "spring.datasource.url=jdbc:h2:mem:radiance-noprofile;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
class AdminBootstrapNoProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Environment environment;

    @Test
    void contextReallyHasNoActiveProfile() {
        assertFalse(environment.getActiveProfiles().length > 0,
                "test must exercise the empty active-profile state");
    }

    @Test
    void withoutAdminPassword_noAdminIsSeeded() {
        assertFalse(userRepository.findByUsername("admin").isPresent(),
                "an admin must not be seeded when no dev/test profile is active");
    }

    @Test
    void wellKnownDevCredentialsCannotAuthenticate() throws Exception {
        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic("admin", "admin12345")))
                .andExpect(status().isUnauthorized());
    }
}
