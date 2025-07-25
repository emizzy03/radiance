package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll() // Public endpoints are accessible without authentication
                .requestMatchers("/api/users/**").permitAll() // Allow user registration endpoints for everyone
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/products/**").permitAll() // Allow GET requests to products for everyone
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/products/**").hasRole("ADMIN") // Restrict POST to ADMIN
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/products/**").hasRole("ADMIN") // Restrict PUT to ADMIN
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN") // Restrict DELETE to ADMIN
                .anyRequest().authenticated()
            )
            .oauth2Login(); // Enable Google OAuth2 login

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
