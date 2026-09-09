package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.example.entity.Role;
import com.example.entity.User;
import com.example.repository.UserRepository;

/**
 * Direct coverage of the Basic-auth user lookup. The previous LazyInitializationException
 * on {@code User.roles} is covered indirectly by HTTP Basic tests; these unit tests lock
 * the mapping itself: unknown users fail closed, empty roles do not NPE, and ROLE_ADMIN
 * is exposed as a Spring authority.
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    void loadUserByUsername_unknownUserThrows() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void loadUserByUsername_mapsAdminRoleToAuthority() {
        User user = new User("boss", "boss@example.com", "hashed");
        Role admin = new Role("ROLE_ADMIN");
        user.setRoles(new HashSet<>(List.of(admin)));
        when(userRepository.findByUsername("boss")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("boss");

        assertThat(details.getUsername()).isEqualTo("boss");
        assertThat(details.getPassword()).isEqualTo("hashed");
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsername_emptyRolesDoesNotThrow() {
        // Self-service registrants have an empty roles collection. Streaming it
        // must not NPE and must produce no authorities.
        User user = new User("shopper", "shopper@example.com", "hashed");
        when(userRepository.findByUsername("shopper")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("shopper");

        assertThat(details.getAuthorities()).isEmpty();
    }
}
