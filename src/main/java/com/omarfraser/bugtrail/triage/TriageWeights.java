package com.omarfraser.bugtrail.triage;

/**
 * The five weights of the triage formula, as fractions that must sum to 1.0.
 *
 * <p>Kept in configuration rather than hardcoded so the model can be tuned against
 * real data later without touching {@link TriageService}. The sum invariant is
 * enforced in the compact constructor — if it did not hold, scores would silently
 * drift outside 0–100 and every bucket boundary would move.
 */
public record TriageWeights(
        double severity,
        double reach,
        double reproducibility,
        double regression,
        double component) {

    /** Tolerance for floating-point comparison of the weight sum. */
    private static final double EPSILON = 1e-9;

    /** The starting model. Revise these once you have resolution data to fit against. */
    public static final TriageWeights DEFAULTS =
            new TriageWeights(0.40, 0.25, 0.15, 0.12, 0.08);

    public TriageWeights {
        requireNonNegative(severity, "severity");
        requireNonNegative(reach, "reach");
        requireNonNegative(reproducibility, "reproducibility");
        requireNonNegative(regression, "regression");
        requireNonNegative(component, "component");

        double sum = severity + reach + reproducibility + regression + component;
        if (Math.abs(sum - 1.0) > EPSILON) {
            throw new IllegalArgumentException(
                    "Triage weights must sum to 1.0 but summed to " + sum);
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (value < 0 || Double.isNaN(value)) {
            throw new IllegalArgumentException(
                    "Triage weight '" + name + "' must be zero or positive but was " + value);
        }
    }
}
