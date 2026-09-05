package com.omarfraser.bugtrail.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behaviour worth pinning down on {@link Project}.
 *
 * <p>These are not "getter tests". Each one covers a place where a silent mistake
 * is possible: constructor wiring, equality semantics, and the identity contract
 * that collections depend on.
 */
class ProjectTest {

    @Test
    @DisplayName("the constructor assigns each argument to its own field")
    void constructorWiresEveryArgumentToTheCorrectField() {
        // Every value is distinct on purpose. If the three strings were similar,
        // a constructor that swapped two of them would still pass.
        Project project = new Project("BT", "BugTrail", "A defect tracker");

        assertThat(project.getProjectKey()).isEqualTo("BT");
        assertThat(project.getName()).isEqualTo("BugTrail");
        assertThat(project.getDescription()).isEqualTo("A defect tracker");
    }

    @Test
    @DisplayName("createdAt is populated at construction, not left null")
    void createdAtIsSetOnConstruction() {
        Project project = new Project("BT", "BugTrail", null);

        assertThat(project.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("id stays null until the database assigns one")
    void idIsNullBeforePersisting() {
        // Worth asserting: it is why equals() cannot be based on id.
        Project project = new Project("BT", "BugTrail", null);

        assertThat(project.getId()).isNull();
    }

    @Test
    @DisplayName("equality is decided by project key, not by object identity")
    void equalsUsesProjectKey() {
        Project first = new Project("BT", "BugTrail", "One description");
        Project sameKey = new Project("BT", "Renamed", "A different description");
        Project otherKey = new Project("XX", "BugTrail", "One description");

        assertThat(first).isEqualTo(sameKey);
        assertThat(first).isNotEqualTo(otherKey);
    }

    @Test
    @DisplayName("two projects with the same key collapse to one entry in a Set")
    void equalProjectsShareAHashCode() {
        // The real reason hashCode has to agree with equals. If it did not, a
        // HashSet would hold both of these and deduplication would silently fail.
        Set<Project> projects = new HashSet<>();
        projects.add(new Project("BT", "BugTrail", null));
        projects.add(new Project("BT", "Renamed", null));

        assertThat(projects).hasSize(1);
    }

    @Test
    @DisplayName("setters change the mutable fields")
    void settersUpdateNameAndDescription() {
        Project project = new Project("BT", "BugTrail", "Original");

        project.setName("BugTrail v2");
        project.setDescription("Updated");

        assertThat(project.getName()).isEqualTo("BugTrail v2");
        assertThat(project.getDescription()).isEqualTo("Updated");
    }

    @Test
    @DisplayName("toString includes the key and name for readable logs")
    void toStringIsUseful() {
        Project project = new Project("BT", "BugTrail", "A defect tracker");

        assertThat(project.toString()).contains("BT").contains("BugTrail");
    }
}