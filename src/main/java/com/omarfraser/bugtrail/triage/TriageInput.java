package com.omarfraser.bugtrail.triage;

import com.omarfraser.bugtrail.domain.ComponentCriticality;
import com.omarfraser.bugtrail.domain.RegressionFlag;
import com.omarfraser.bugtrail.domain.Reproducibility;
import com.omarfraser.bugtrail.domain.Severity;

/**
 * Everything the triage engine needs to score a defect.
 *
 * <p>A record rather than the Ticket entity itself, so the engine can be unit-tested
 * without a database, a Spring context, or a persistence layer. Keeping the scoring
 * input separate from the persisted entity is what lets the whole triage package run
 * in milliseconds.
 *
 * @param affectedUserFraction proportion of users hitting this defect, 0.0 to 1.0
 */
public record TriageInput(
        Severity severity,
        double affectedUserFraction,
        Reproducibility reproducibility,
        RegressionFlag regressionFlag,
        ComponentCriticality componentCriticality) {

    public TriageInput {
        if (severity == null
                || reproducibility == null
                || regressionFlag == null
                || componentCriticality == null) {
            throw new IllegalArgumentException("Triage inputs must not be null");
        }
        if (affectedUserFraction < 0.0
                || affectedUserFraction > 1.0
                || Double.isNaN(affectedUserFraction)) {
            throw new IllegalArgumentException(
                    "affectedUserFraction must be between 0.0 and 1.0 but was "
                            + affectedUserFraction);
        }
    }
}
