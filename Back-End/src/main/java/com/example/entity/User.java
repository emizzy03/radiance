package com.example.entity;
import java.util.Collection;
import java.util.HashSet;

import com.example.validation.OnCreate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
@Entity
@Table(name = "users")
public class User {

    // READ_ONLY: the id is server-assigned and must never be set from client
    // input (prevents overwriting arbitrary records on create/update).
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(groups = OnCreate.class, message = "Username is required")
    @Column(nullable = false, unique = true)
    private String username;

    @NotBlank(groups = OnCreate.class, message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Column(nullable = false, unique = true)
    private String email;

    // WRITE_ONLY: accepted during deserialization (registration) but never
    // serialized back to clients, so passwords are not leaked in responses.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(groups = OnCreate.class, message = "Password is required")
    @Size(min = 8, groups = OnCreate.class, message = "Password must be at least 8 characters long")
    @Column(nullable = false)
    private String password;

    // JsonIgnore: roles are never accepted from nor exposed to clients. They are
    // assigned only through the dedicated, ADMIN-guarded role-management
    // endpoints. Accepting them on input would let a self-service registrant
    // grant themselves ROLE_ADMIN via mass assignment; exposing them on output
    // is unnecessary data exposure (and would trigger lazy-loading outside a
    // transaction with open-in-view disabled).
    @JsonIgnore
    @ManyToMany
    @JoinTable(name = "user_roles",
            joinColumns = @jakarta.persistence.JoinColumn(name = "user_id"),
            inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "role_id"))
    private Collection<Role> roles = new HashSet<>();

    // Getters and Setters for roles
    public Collection<Role> getRoles() {
        return roles;
    }
    public void setRoles(Collection<Role> roles) {
        this.roles = roles;
    }

    // Default constructor
    public User() {
    }

    // Constructor with parameters
    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    // Set the password directly
    public void setPassword(String password) {
        this.password = password;
    }
}
