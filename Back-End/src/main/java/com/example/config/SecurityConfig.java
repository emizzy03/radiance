package com.example.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;

import org.springframework.http.HttpMethod;

// import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
// import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private Environment environment;

    // Commenting out Google OAuth2 configuration for testing
    /*
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(OAuth2ClientProperties clientProperties) {
        List<ClientRegistration> registrations = clientProperties
                .getRegistration()
                .entrySet()
                .stream()
                .map(entry -> getRegistration(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new InMemoryClientRegistrationRepository(registrations);
    }
    private ClientRegistration getRegistration(String registrationId,
            OAuth2ClientProperties.Registration registration) {
        if ("google".equals(registrationId)) {
            return CommonOAuth2Provider.GOOGLE.getBuilder(registrationId)
                    .clientId(registration.getClientId())
                    .clientSecret(registration.getClientSecret())
                    .build();
        }
        return null;
    }
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection is intentionally disabled: this is a stateless API
                // (SessionCreationPolicy.STATELESS) authenticated per-request with HTTP
                // Basic and no server-side session or auth cookie. Without an
                // ambient, browser-attached credential there is no CSRF vector for the
                // state-changing endpoints. If cookie/session auth is ever introduced,
                // CSRF protection must be re-enabled for those flows.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll() // Public endpoints
                // Static admin page + shared static assets must be reachable so
                // the browser can load the page before authenticating API calls.
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/admin", "/admin/", "/admin/**",
                        "/shop", "/shop/", "/shop/**", "/media/**",
                        "/css/**", "/js/**", "/favicon.ico").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/users").permitAll() // Allow user registration
                // Role management and admin creation are ADMIN-only (prevents any
                // authenticated user from granting themselves ROLE_ADMIN).
                .requestMatchers(HttpMethod.POST, "/api/users/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/users/*/roles/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/users/*/roles/**").hasRole("ADMIN")
                // Fine-grained authorization (admin-only listing, self-or-admin
                // ownership on read/update/delete) is enforced with @PreAuthorize
                // in UserController; require authentication here as a coarse gate.
                .requestMatchers(HttpMethod.GET, "/api/users/**").authenticated() // Require authentication for GET
                .requestMatchers(HttpMethod.PATCH, "/api/users/**").authenticated() // Require authentication for PATCH
                .requestMatchers(HttpMethod.DELETE, "/api/users/**").authenticated() // Require authentication for DELETE
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll() // Allow GET requests to products
                .requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/purchases").permitAll() // Public checkout
                .requestMatchers(HttpMethod.GET, "/api/purchases/**").hasRole("ADMIN") // Admin reporting
                .anyRequest().authenticated()
                )
                // .oauth2Login(org.springframework.security.config.Customizer.withDefaults())
                .httpBasic(org.springframework.security.config.Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Origins come from configuration (comma-separated) and are always an
        // explicit allow-list. Because credentials are enabled, a wildcard "*"
        // origin is forbidden by the CORS spec and would be a serious risk, so we
        // reject it explicitly and fall back to the safe local dev origin.
        String configured = environment.getProperty("app.cors.allowed-origins", "http://localhost:3000");
        List<String> origins = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty() && !"*".equals(o))
                .collect(Collectors.toList());
        if (origins.isEmpty()) {
            origins = List.of("http://localhost:3000");
        }
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        // Safe to keep credentials because origins above are never wildcarded.
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
