package com.omarfraser.bugtrail.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A person who can log in to BugTrail.
 * 
 * <p>Named {@code AppUser} rather than {@code User} because {@code user} is a 
 * reserved word in PostgreSQL -- an unquoted {@code SELECT * FROM user} returns
 * the current database role, not your table
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    /**
     * BCrypt hash. Never the plaintext password, and never logged.
     */
     // TODO(phase-3): populate via PasswordEncoder when Spring Security lands.
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Stored as the enum's NAME, not its position. See the note below on
     * {@code EnumType.STRING} -- the default here is actively dangerous.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    /**
     * Required by JPA. Application code should use the constructor below.
     */
    protected AppUser() {
    }

    public AppUser(String username, String email, String displayName,
                   String passwordHash, Role role) {
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
    }
    
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setEmail(String email) { this.email = email; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setRole(Role role) { this.role = role; }

    /**
     * Deactivates the account without deleting it.
     *
     * <p>Deleting a user would orphan every ticket they reported or were assigned,
     * and destroy the audit trail. Deactivation preserves history while blocking
     * sign-in.
     */
    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppUser other)) return false;
        return username != null && username.equals (other.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

     /**
     * Deliberately excludes {@code passwordHash}. Entities end up in log lines and
     * exception messages, and a toString that prints credentials is how hashes leak
     * into logfiles that are far less protected than the database they came from.
     */
    @Override
    public String toString() {
        return "AppUser[" + username + " " + role + "]";
    }
}