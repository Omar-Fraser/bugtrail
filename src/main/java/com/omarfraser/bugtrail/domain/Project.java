package com.omarfraser.bugtrail.domain; 

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, unique = true, length = 16)
    private String projectKey;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Project() {
        // Required but not for application, used for construction code block below
    }

    public Project(String projectKey, String name, String description) {
        this.projectKey = projectKey;
        this.name = name;
        this.description = description; 
    }

    public Long getId() {return id; }
    public String getProjectKey() { return projectKey; }
    public String getName() {return name; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() {return createdAt; }

    public void setName(String name) {this.name = name; }
    public void setDescription(String description) {this.description = description; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true; 
        if (!(o instanceof Project other)) return false;
        return projectKey != null && projectKey.equals(other.projectKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectKey);
    }
    @Override
    public String toString() {
        return "Project[" + projectKey +" " + name + "]";
    }
}
