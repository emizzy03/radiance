package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.entity.Role;
import com.example.entity.User;
import com.example.repository.RoleRepository;
import com.example.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

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
    void assignRoleToUser_doesNotThrowWhenUserHasNoRolesYet() {
        // Regression test for the NullPointerException that occurred because
        // User.roles was never initialized.
        Role role = new Role("ROLE_USER");
        role.setId(5L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ROLE_USER")).thenReturn(role);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> userService.assignRoleToUser(1L, "ROLE_USER"))
                .doesNotThrowAnyException();

        assertThat(user.getRoles()).contains(role);
    }

    @Test
    void removeRoleFromUser_doesNotThrowWhenUserHasNoRolesYet() {
        Role role = new Role("ROLE_USER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ROLE_USER")).thenReturn(role);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> userService.removeRoleFromUser(1L, "ROLE_USER"))
                .doesNotThrowAnyException();
    }

    @Test
    void createUser_encodesPassword() {
        when(passwordEncoder.encode("plaintext")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User toCreate = new User("bob", "bob@example.com", "plaintext");
        User created = userService.createUser(toCreate);

        assertThat(created.getPassword()).isEqualTo("hashed");
    }

    @Test
    void createAdminUser_createsRoleWhenMissing() {
        // Regression test: createAdminUser previously threw when ROLE_ADMIN
        // did not already exist.
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(null);
        Role savedRole = new Role("ROLE_ADMIN");
        savedRole.setId(9L);
        when(roleRepository.save(any(Role.class))).thenReturn(savedRole);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User admin = userService.createAdminUser("admin", "admin@example.com", "secretpass");

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        org.mockito.Mockito.verify(roleRepository).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getName()).isEqualTo("ROLE_ADMIN");
        assertThat(admin.getRoles()).contains(savedRole);
    }

    @Test
    void updateUser_doesNotNullOutFieldsOnPartialUpdate() {
        // Regression test for PATCH semantics blanking existing fields.
        User existing = new User("olduser", "old@example.com", "encoded");
        existing.setId(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User patch = new User();
        patch.setUsername("newuser"); // email intentionally left null

        User updated = userService.updateUser(2L, patch);

        assertThat(updated.getUsername()).isEqualTo("newuser");
        assertThat(updated.getEmail()).isEqualTo("old@example.com");
    }

    @Test
    void createAdminUser_rejectsDuplicateUsername() {
        when(userRepository.existsByUsername("admin")).thenReturn(true);

        assertThatThrownBy(() -> userService.createAdminUser("admin", "admin@example.com", "secretpass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");

        org.mockito.Mockito.verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createAdminUser_rejectsDuplicateEmail() {
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createAdminUser("admin", "admin@example.com", "secretpass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");

        org.mockito.Mockito.verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void assignRoleToUser_rejectsUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.assignRoleToUser(99L, "ROLE_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void assignRoleToUser_rejectsUnknownRole() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ROLE_NOPE")).thenReturn(null);

        assertThatThrownBy(() -> userService.assignRoleToUser(1L, "ROLE_NOPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role not found");
    }

    @Test
    void updatePassword_rejectsUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updatePassword(99L, "oldpass12", "newpass12"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void updatePassword_rejectsIncorrectCurrentPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pass", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> userService.updatePassword(1L, "wrong-pass", "newpass12"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current password is incorrect");

        org.mockito.Mockito.verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updatePassword_rejectsShortNewPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldpass12", "encoded")).thenReturn(true);

        assertThatThrownBy(() -> userService.updatePassword(1L, "oldpass12", "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 8");

        org.mockito.Mockito.verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updatePassword_encodesNewPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldpass12", "encoded")).thenReturn(true);
        when(passwordEncoder.encode("newpass12")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.updatePassword(1L, "oldpass12", "newpass12");

        assertThat(updated.getPassword()).isEqualTo("new-hash");
    }
}
