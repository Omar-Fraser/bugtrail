package com.omarfraser.bugtrail.triage;

import com.omarfraser.bugtrail.domain.ComponentCriticality;
import com.omarfraser.bugtrail.domain.Priority;
import com.omarfraser.bugtrail.domain.Severity;

/**
 * Computes a defect's priority from five weighted inputs.
 *
 * <p>Deliberately free of Spring annotations, JPA, and any I/O. It takes a record in
 * and returns a record out, which is what lets {@code TriageServiceTest} cover it
 * exhaustively without starting a context. Wire it up with an {@code @Bean} method in
 * your configuration class rather than annotating it here.
 *
 * <p>The scoring model:
 * <pre>
 *   score = 0.40 * severity
 *         + 0.25 * reach          (log-scaled — see {@link #reachScore(double)})
 *         + 0.15 * reproducibility
 *         + 0.12 * regression
 *         + 0.08 * componentCriticality
 * </pre>
 */
public class TriageService {

    private final TriageWeights weights;

    public TriageService(TriageWeights weights) {
        if (weights == null) {
            throw new IllegalArgumentException("TriageWeights must not be null");
        }
        this.weights = weights;
    }

    /** Convenience constructor using {@link TriageWeights#DEFAULTS}. */
    public TriageService() {
        this(TriageWeights.DEFAULTS);
    }

    /**
     * Scores a defect and buckets it into a priority.
     *
     * @throws IllegalArgumentException if {@code input} is null
     */
    public TriageResult triage(TriageInput input) {
        if (input == null) {
            throw new IllegalArgumentException("TriageInput must not be null");
        }

        double score = weights.severity() * input.severity().weight()
                + weights.reach() * reachScore(input.affectedUserFraction())
                + weights.reproducibility() * input.reproducibility().weight()
                + weights.regression() * input.regressionFlag().weight()
                + weights.component() * input.componentCriticality().weight();

        double rounded = roundToTwoDecimals(score);
        boolean floored = appliesP0Floor(input);
        Priority priority = floored ? Priority.P0 : Priority.fromScore(rounded);

        return new TriageResult(rounded, priority, floored);
    }

    /**
     * The P0 floor rule: a total failure on a critical-path component is always P0,
     * regardless of how few users have hit it yet.
     *
     * <p>Without this, a BLOCKER on authentication that only one user has reported
     * scores around 56 and lands in P2 — which is exactly the ticket you cannot
     * afford to leave sitting in a backlog.
     */
    private boolean appliesP0Floor(TriageInput input) {
        return input.severity() == Severity.BLOCKER
                && input.componentCriticality() == ComponentCriticality.CRITICAL_PATH;
    }

    /**
     * Converts a raw fraction of affected users into a 0–100 score on a log scale.
     *
     * <p>Log-scaled because the jump from 1% of users to 10% matters far more than
     * the jump from 80% to 90% — by 80% you already know it is everyone's problem.
     * Linear scaling would let widespread-but-cosmetic defects crowd out narrow
     * catastrophic ones.
     *
     * <p>Anchors: 0.0 maps to 0, 0.1 maps to about 27.9, and 1.0 maps to exactly 100.
     *
     * @param fraction proportion of affected users, 0.0 to 1.0
     */
    static double reachScore(double fraction) {
        if (fraction < 0.0 || fraction > 1.0 || Double.isNaN(fraction)) {
            throw new IllegalArgumentException(
                    "Affected user fraction must be between 0.0 and 1.0 but was " + fraction);
        }
        return 100.0 * Math.log10(1.0 + 9.0 * fraction);
    }

    private static double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
