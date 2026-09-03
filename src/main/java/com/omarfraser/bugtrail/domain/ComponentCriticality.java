package com.omarfraser.bugtrail.domain;

/**
 * How central the affected component is to the product working at all.
 *
 * <p>Carries the smallest weight of the five inputs, because it is the coarsest
 * signal — but it also drives the P0 floor rule in
 * {@code TriageService}: a BLOCKER on a CRITICAL_PATH component is always P0,
 * whatever the arithmetic says.
 */
public enum ComponentCriticality {

    /** Authentication, payments, data integrity — nothing works without these. */
    CRITICAL_PATH(100),

    /** Primary product features users touch every session. */
    CORE(70),

    /** Settings, preferences, secondary tooling. */
    PERIPHERAL(40);

    private final int weight;

    ComponentCriticality(int weight) {
        this.weight = weight;
    }

    /** Normalized 0–100 contribution to the triage score. */
    public int weight() {
        return weight;
    }
}
