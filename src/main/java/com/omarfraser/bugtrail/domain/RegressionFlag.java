package com.omarfraser.bugtrail.domain;

/**
 * Whether this behaviour worked in a previous release.
 *
 * <p>Regressions are weighted far above new defects on purpose. A regression means
 * something that used to work no longer does, which is both a broken promise to
 * users and evidence that the test suite failed to catch it.
 */
public enum RegressionFlag {

    /** Worked in a prior release, broken now. */
    REGRESSION(100),

    /** Has never worked; the defect is in new or previously untested behaviour. */
    NEW_DEFECT(40);

    private final int weight;

    RegressionFlag(int weight) {
        this.weight = weight;
    }

    /** Normalized 0–100 contribution to the triage score. */
    public int weight() {
        return weight;
    }
}
