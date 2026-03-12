package com.example.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import org.springframework.core.env.Environment;

import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private Environment environment;

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher.Builder mvc) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                .requestMatchers(mvc.pattern("/api/auth/**")).permitAll() // Allow authentication endpoints
                .requestMatchers(mvc.pattern("/api/public/**")).permitAll() // Public endpoints
                .requestMatchers(mvc.pattern("/uploads/**")).permitAll() // Allow public access to uploaded images
                .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/upload/**")).hasRole("ADMIN") // Only admins can upload
                .requestMatchers(mvc.pattern(HttpMethod.DELETE, "/api/upload/**")).hasRole("ADMIN") // Only admins can delete
                .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/users")).permitAll() // Allow user registration
                .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/users/**")).authenticated() // Require authentication for GET
                .requestMatchers(mvc.pattern(HttpMethod.PATCH, "/api/users/**")).authenticated() // Require authentication for PATCH
                .requestMatchers(mvc.pattern(HttpMethod.DELETE, "/api/users/**")).authenticated() // Require authentication for DELETE
                .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/products/**")).permitAll() // Allow GET requests to products
                .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/products/**")).hasRole("ADMIN")
                .requestMatchers(mvc.pattern(HttpMethod.PATCH, "/api/products/**")).hasRole("ADMIN")
                .requestMatchers(mvc.pattern(HttpMethod.DELETE, "/api/products/**")).hasRole("ADMIN")
                .anyRequest().authenticated()
                )

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @org.springframework.context.annotation.Scope("prototype")
    @Bean
    org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher.Builder mvc(org.springframework.web.servlet.handler.HandlerMappingIntrospector introspector) {
        return new org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher.Builder(introspector);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(environment.getProperty("app.cors.allowed-origins", "http://localhost:3000")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
