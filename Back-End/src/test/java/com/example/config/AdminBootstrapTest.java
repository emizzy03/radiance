package com.example.config;

import static org.assertj.core.api.Assertions.assertThatCode;
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
 * Verifies the admin-seeding bootstrap never falls back to a hard-coded default
 * password outside of a development profile.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminBootstrap adminBootstrap;

    private void setEnvironment(MockEnvironment env) {
        ReflectionTestUtils.setField(adminBootstrap, "environment", env);
    }

    @Test
    void nonDevProfile_withoutAdminPassword_skipsSeeding() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        setEnvironment(env);

        when(userService.existsByUsername("admin")).thenReturn(false);

        adminBootstrap.run();

        verify(userService, never()).createAdminUser(anyString(), anyString(), anyString());
    }

    @Test
    void nonDevProfile_withAdminPassword_seedsWithProvidedPassword() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("ADMIN_PASSWORD", "super-secret-strong-password");
        setEnvironment(env);

        when(userService.existsByUsername("admin")).thenReturn(false);

        adminBootstrap.run();

        verify(userService).createAdminUser(eq("admin"), anyString(), eq("super-secret-strong-password"));
    }

    @Test
    void devProfile_withoutAdminPassword_usesDevDefault() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        setEnvironment(env);

        when(userService.existsByUsername("admin")).thenReturn(false);

        adminBootstrap.run();

        verify(userService).createAdminUser(eq("admin"), anyString(), eq("admin12345"));
    }

    @Test
    void defaultProfile_withoutAdminPassword_usesDevDefault() throws Exception {
        MockEnvironment env = new MockEnvironment();
        // no active profiles == plain local run == dev
        setEnvironment(env);

        when(userService.existsByUsername("admin")).thenReturn(false);

        adminBootstrap.run();

        verify(userService).createAdminUser(eq("admin"), anyString(), eq("admin12345"));
    }

    @Test
    void existingAdmin_isNotRecreated() throws Exception {
        MockEnvironment env = new MockEnvironment();
        setEnvironment(env);

        when(userService.existsByUsername("admin")).thenReturn(true);

        adminBootstrap.run();

        verify(userService, never()).createAdminUser(anyString(), anyString(), any());
    }

    @Test
    void createAdminUserFailure_doesNotAbortStartup() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        setEnvironment(env);

        when(userService.existsByUsername("admin")).thenReturn(false);
        when(userService.createAdminUser(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Username already exists"));

        assertThatCode(() -> adminBootstrap.run()).doesNotThrowAnyException();
    }
}
