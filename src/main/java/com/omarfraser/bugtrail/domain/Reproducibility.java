package com.omarfraser.bugtrail.domain;

/**
 * How reliably the defect can be reproduced.
 *
 * <p>Note that intermittent defects are not treated as harmless. They are harder
 * to diagnose and easier to ship, so ONCE still carries real weight rather than zero.
 */
public enum Reproducibility {

    /** Reproduces on every attempt. */
    ALWAYS(100),

    /** Reproduces sometimes, under conditions not yet pinned down. */
    INTERMITTENT(60),

    /** Observed a single time and not reproduced since. */
    ONCE(25);

    private final int weight;

    Reproducibility(int weight) {
        this.weight = weight;
    }

    /** Normalized 0–100 contribution to the triage score. */
    public int weight() {
        return weight;
    }
}
