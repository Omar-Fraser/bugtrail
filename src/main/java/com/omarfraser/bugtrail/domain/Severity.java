package com.omarfraser.bugtrail.domain;

/**
 * How badly the software misbehaves. Set by the reporter, who can observe it.
 *
 * <p>Severity is NOT priority. A trivial typo on the landing page is low severity
 * and can still be P0; a crash in a feature three users have enabled is BLOCKER
 * severity and can still wait for the next release. See {@link Priority}.
 */
public enum Severity {

    /** Total failure. No workaround exists. */
    BLOCKER(100),

    /** Major function broken. A painful workaround exists. */
    CRITICAL(80),

    /** A feature behaves incorrectly but the product remains usable. */
    MAJOR(60),

    /** Noticeable but low impact — layout, wording, a cosmetic glitch. */
    MINOR(35),

    /** Barely worth reporting on its own. */
    TRIVIAL(15);

    private final int weight;

    Severity(int weight) {
        this.weight = weight;
    }

    /** Normalized 0–100 contribution to the triage score. */
    public int weight() {
        return weight;
    }
}
