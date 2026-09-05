package com.omarfraser.bugtrail.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behaviour and safety guarantees for {@link AppUser}.
 * 
 * <p>The toString is not a formatting check -- it is a security control.
 * See its comment.
 */

class AppUserTest {
    @Test
    @DisplayName("the constructor assigns each argument to its own field")
    void constructorWiresEveryArgumentToTheCorrectField() {
        AppUser user = new AppUser(
            "ofraser",
            "omar@example.com",
            "Omar Fraser",
            "$2a$10$NOTAREALHASH0123456789",
            Role.DEVELOPER);
        
        assertThat(user.getUsername()).isEqualTo("ofraser");
        assertThat(user.getEmail()).isEqualTo("omar@example.com");
        assertThat(user.getDisplayName()).isEqualTo("Omar Fraser");
        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$NOTAREALHASH0123456789");
        assertThat(user.getRole()).isEqualTo(Role.DEVELOPER);
    }
    /**
     * Builds a valid user for tests that do not care about the specific values.
     */
    private AppUser sampleUser() {
        return new AppUser(
                "ofraser",
                "omar@example.com",
                "Omar Fraser",
                "$2a$10$NOTAREALHASH0123456789",
                Role.DEVELOPER);
    }

    @Test
    @DisplayName("toString never exposes the password hash")
    void toStringDoesNotLeakThePasswordHash() {
        // This is a security control expressed as a test. Entities land in log 
        // lines and exception messages constantly. If someone adds passwordHash
        // to toString for debugging convenience, this fails and stops it from reaching
        // a logfile that is backed up and shipped to third-party log tooling.
        AppUser user = sampleUser();
        
        assertThat(user.toString()).doesNotContain("$2a$10$NOTAREALHASH0123456789");
        assertThat(user.toString()).contains("ofraser");
    }

    @Test
    @DisplayName("a new user is active until explicitly deactivated")
    void newUserIsActiveByDefault() {
        assertThat(sampleUser().isActive()).isTrue();
    }

    @Test
    @DisplayName("deactivate blocks sign-in without destroying the account")
    void deactivateAndReactivateFlipTheFlag() {
        AppUser user = sampleUser();

        user.deactivate();
        assertThat(user.isActive()).isFalse();

        user.reactivate();
        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("equality is decided by username, not by the other fields")
    void equalsUsesUsername() {
        AppUser first = sampleUser();
        AppUser sameUsername = new AppUser(
            "ofraser", "different@example.com", "Someone Else",
            "$2a$10$ADIFFERENTFAKEHASH00000", Role.ADMIN);

        assertThat(first).isEqualTo(sameUsername);
        assertThat(first).isNotEqualTo(otherUsername);
        assertThat(first).hasSameHashCodeAs(sameUsername);
    }
 }