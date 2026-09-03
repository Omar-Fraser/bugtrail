package com.omarfraser.bugtrail.domain;

/**
 * How urgently the defect should be fixed. Computed by the system, never chosen
 * by the reporter — a reporter cannot see user reach, regression history, or which
 * components sit on the critical path, so asking them to pick produces noise.
 */
public enum Priority {

    /** Blocks the release. Fix now. */
    P0(80),

    /** Fix this sprint. */
    P1(60),

    /** Fix in the next release. */
    P2(40),

    /** Icebox. Revisit quarterly. */
    P3(0);

    private final int minScore;

    Priority(int minScore) {
        this.minScore = minScore;
    }

    /** Lowest triage score that lands in this bucket, inclusive. */
    public int minScore() {
        return minScore;
    }

    /**
     * Buckets a triage score. Boundaries are inclusive at the bottom, so a score of
     * exactly 80.0 is P0 while 79.999 is P1 — the case worth a dedicated test.
     *
     * @param score weighted triage score in the range 0–100
     * @throws IllegalArgumentException if the score falls outside 0–100
     */
    public static Priority fromScore(double score) {
        if (score < 0 || score > 100 || Double.isNaN(score)) {
            throw new IllegalArgumentException(
                    "Triage score must be between 0 and 100 but was " + score);
        }
        if (score >= P0.minScore) {
            return P0;
        }
        if (score >= P1.minScore) {
            return P1;
        }
        if (score >= P2.minScore) {
            return P2;
        }
        return P3;
    }
}
