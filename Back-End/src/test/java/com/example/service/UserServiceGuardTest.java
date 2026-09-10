package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
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
 * Guard clauses on role removal, account deletion, and missing-user updates.
 * Complementary to {@link UserServiceTest} so open coverage PRs can merge
 * without colliding on that file.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceGuardTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("alice", "alice@example.com", "encoded");
        user.setId(1L);
    }

    @Test
    void removeRoleFromUser_rejectsUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.removeRoleFromUser(99L, "ROLE_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void removeRoleFromUser_rejectsUnknownRole() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ROLE_NOPE")).thenReturn(null);

        assertThatThrownBy(() -> userService.removeRoleFromUser(1L, "ROLE_NOPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role not found");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void removeRoleFromUser_removesMatchingRole() {
        Role role = new Role("ROLE_USER");
        user.getRoles().add(role);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ROLE_USER")).thenReturn(role);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.removeRoleFromUser(1L, "ROLE_USER");

        assertThat(user.getRoles()).doesNotContain(role);
        verify(userRepository).save(user);
    }

    @Test
    void deleteUser_returnsFalseWhenMissing() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThat(userService.deleteUser(99L)).isFalse();
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void deleteUser_deletesWhenPresent() {
        when(userRepository.existsById(1L)).thenReturn(true);

        assertThat(userService.deleteUser(1L)).isTrue();
        verify(userRepository).deleteById(1L);
    }

    @Test
    void updateUser_returnsNullWhenMissing() {
        User patch = new User();
        patch.setEmail("new@example.com");
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(userService.updateUser(99L, patch)).isNull();
        verify(userRepository, never()).save(any(User.class));
    }
}
