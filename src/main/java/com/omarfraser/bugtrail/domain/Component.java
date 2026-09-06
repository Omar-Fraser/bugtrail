package com.omarfraser.bugtrail.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A part of a project that a defect can be filed against -- authentication,
 * the ticket board, the webhook receiver.
 */
@Entity
@Table(name = "component")
public class Component {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The project this component belongs to.
     *
     * <p>LAZY is deliberate -- the JPA default of EAGER means loading 50 components
     * fires 51 queries. See the class Javadoc.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /**
     * Feeds the triage score, and drives the P0 floor rule: a BLOCKER on a
     * CRITICAL_PATH component is always P0 regardless of the computed score.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "criticality", nullable = false, length = 16)
    private ComponentCriticality criticality;
    /**
     * Required by JPA. Application code should use the constructor below.
     */
    protected Component() {
    }

    public Component(Project project, String name, ComponentCriticality criticality){
        this.project = project;
        this.name = name;
        this.criticality = criticality;
    }
    public Long getId() { return id; }

    public Project getProject() { return project; }

    public String getName() { return name; }

    public ComponentCriticality getCriticality() { return criticality; }

    public void setName(String name) {this.name = name; }

    public void setCriticality(ComponentCriticality criticality) {
        this.criticality = criticality; 
    }

     /**
     * Identity is based on the database id, not on a natural key.
     *
     * <p>{@code Project} and {@code AppUser} use natural keys (project key,
     * username). Component's natural key would be (project, name) -- but reading
     * {@code project} here would touch a LAZY association, and equals() must never
     * trigger a database query. So id it is.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Component other)) return false;
        return id != null && id.equals(other.id);
    }

    /**
     * Constant on purpose -- see the note. Not a deliberate mistake.
     * 
     * <p>An id-based hashCode changes when the entity is saved in the database 
     * assigns an id. If the object was already in HashSet it becomes 
     * unreachable: the set looks in the old pool and finds nothing. A constant
     * keeps the contract intact across that transaction. 
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        // Deliberately does not include project -- it is lazy, and a toString that 
        // triggers a query (or throws LazyInitializationException) is a trap that
        // only shows up in logging code.
        return "Component[" + name + " " + criticality + "]";   
    }
}