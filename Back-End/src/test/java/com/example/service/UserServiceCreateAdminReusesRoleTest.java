package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.entity.Role;
import com.example.entity.User;
import com.example.repository.RoleRepository;
import com.example.repository.UserRepository;

/**
 * After bootstrap, {@code ROLE_ADMIN} already exists. Creating another admin
 * must attach that managed role and must not insert a duplicate role row
 * (which would break {@code hasRole("ADMIN")} lookups by name). Complements
 * {@code UserServiceTest#createAdminUser_createsRoleWhenMissing}.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceCreateAdminReusesRoleTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createAdminUser_reusesExistingAdminRole() {
        Role existing = new Role("ROLE_ADMIN");
        existing.setId(1L);

        when(userRepository.existsByUsername("ops")).thenReturn(false);
        when(userRepository.existsByEmail("ops@radiance.local")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(existing);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User admin = userService.createAdminUser("ops", "ops@radiance.local", "secretpass");

        verify(roleRepository, never()).save(any(Role.class));
        assertThat(admin.getRoles()).containsExactly(existing);
        assertThat(admin.getPassword()).isEqualTo("hashed");
    }
}
