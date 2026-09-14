package com.example.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.service.UserService;

/**
 * Deployment bootstrap must honor explicit ADMIN_* credentials and must not
 * recreate an already-seeded custom username. Complementary to
 * {@link AdminBootstrapTest} so open coverage PRs can merge without colliding.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapCustomCredentialsTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminBootstrap adminBootstrap;

    private void setEnvironment(MockEnvironment env) {
        ReflectionTestUtils.setField(adminBootstrap, "environment", env);
    }

    @Test
    void honorsCustomUsernameEmailAndPassword() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("ADMIN_USERNAME", "ops");
        env.setProperty("ADMIN_EMAIL", "ops@radiance.local");
        env.setProperty("ADMIN_PASSWORD", "custom-pass-123");
        setEnvironment(env);

        when(userService.existsByUsername("ops")).thenReturn(false);

        adminBootstrap.run();

        verify(userService).createAdminUser(eq("ops"), eq("ops@radiance.local"), eq("custom-pass-123"));
    }

    @Test
    void existingCustomUsername_isNotRecreated() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("ADMIN_USERNAME", "ops");
        env.setProperty("ADMIN_PASSWORD", "custom-pass-123");
        setEnvironment(env);

        when(userService.existsByUsername("ops")).thenReturn(true);

        adminBootstrap.run();

        verify(userService).existsByUsername("ops");
        verify(userService, never()).createAdminUser(anyString(), anyString(), any());
    }

    @Test
    void testProfile_withoutAdminPassword_usesDevDefault() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        setEnvironment(env);

        when(userService.existsByUsername("admin")).thenReturn(false);

        adminBootstrap.run();

        verify(userService).createAdminUser(eq("admin"), anyString(), eq("admin12345"));
    }

    @Test
    void prodProfile_blankAdminPassword_skipsSeeding() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("ADMIN_PASSWORD", "   ");
        setEnvironment(env);

        when(userService.existsByUsername("admin")).thenReturn(false);

        adminBootstrap.run();

        verify(userService, never()).createAdminUser(anyString(), anyString(), anyString());
    }
}
