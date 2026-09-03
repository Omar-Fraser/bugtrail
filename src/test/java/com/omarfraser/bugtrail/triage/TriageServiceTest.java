package com.omarfraser.bugtrail.triage;

import com.omarfraser.bugtrail.domain.ComponentCriticality;
import com.omarfraser.bugtrail.domain.Priority;
import com.omarfraser.bugtrail.domain.RegressionFlag;
import com.omarfraser.bugtrail.domain.Reproducibility;
import com.omarfraser.bugtrail.domain.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * The triage engine is the piece of BugTrail with real logic in it, so it gets the
 * most testing. Three techniques are on display here, and naming them is half the
 * point of writing them this way:
 *
 * <ul>
 *   <li><b>Boundary value analysis</b> — the bucket edges (79.99 / 80.00) are where
 *       an off-by-one lives, not the middle of a range.</li>
 *   <li><b>Equivalence partitioning</b> — one representative case per bucket rather
 *       than fifty cases that all exercise the same branch.</li>
 *   <li><b>Negative testing</b> — the invalid inputs are asserted as carefully as
 *       the valid ones. Half these tests are about what should be impossible.</li>
 * </ul>
 */
class TriageServiceTest {

    private final TriageService service = new TriageService(TriageWeights.DEFAULTS);

    // ------------------------------------------------------------------
    // Scoring range
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("score range")
    class ScoreRange {

        @Test
        @DisplayName("the worst possible defect scores exactly 100")
        void worstCaseScoresOneHundred() {
            TriageResult result = service.triage(new TriageInput(
                    Severity.BLOCKER,
                    1.0,
                    Reproducibility.ALWAYS,
                    RegressionFlag.REGRESSION,
                    ComponentCriticality.CRITICAL_PATH));

            assertThat(result.score()).isEqualTo(100.0);
            assertThat(result.priority()).isEqualTo(Priority.P0);
        }

        @Test
        @DisplayName("the mildest possible defect scores 17.75, not zero")
        void mildestCaseStillScoresAboveZero() {
            // Worth asserting explicitly: even the least urgent defect carries weight,
            // because reproducibility, regression status and component all have non-zero
            // floors. A score of 0 would mean "not a defect", which is REJECTED's job.
            TriageResult result = service.triage(new TriageInput(
                    Severity.TRIVIAL,
                    0.0,
                    Reproducibility.ONCE,
                    RegressionFlag.NEW_DEFECT,
                    ComponentCriticality.PERIPHERAL));

            assertThat(result.score()).isEqualTo(17.75);
            assertThat(result.priority()).isEqualTo(Priority.P3);
        }
    }

    // ------------------------------------------------------------------
    // Bucket boundaries
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("bucket boundaries")
    class BucketBoundaries {

        @ParameterizedTest(name = "score {0} lands in {1}")
        @CsvSource({
                "100.0, P0",
                " 80.0, P0",   // inclusive lower edge of P0
                " 79.99, P1",  // one hundredth below it
                " 60.0, P1",
                " 59.99, P2",
                " 40.0, P2",
                " 39.99, P3",
                "  0.0, P3"
        })
        void bucketsAtTheEdges(double score, Priority expected) {
            assertThat(Priority.fromScore(score)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "score {0} is rejected")
        @ValueSource(doubles = {-0.01, -1.0, 100.01, 1000.0, Double.NaN})
        void rejectsScoresOutsideTheValidRange(double score) {
            assertThatThrownBy(() -> Priority.fromScore(score))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 0 and 100");
        }
    }

    // ------------------------------------------------------------------
    // Reach
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("reach scaling")
    class ReachScaling {

        @ParameterizedTest(name = "{0} of users affected scores {1}")
        @CsvSource({
                "0.0,     0.00",
                "0.1,    27.88",
                "0.5,    74.04",
                "1.0,   100.00"
        })
        void mapsFractionOfUsersOntoALogScale(double fraction, double expected) {
            assertThat(TriageService.reachScore(fraction)).isCloseTo(expected, within(0.01));
        }

        @Test
        @DisplayName("the first 10% of users matters more than the last 10%")
        void isLogScaledNotLinear() {
            // This is the whole reason for the log scale. If someone "simplifies" it
            // to linear later, this assertion is what stops them.
            double firstTenPercent = TriageService.reachScore(0.1) - TriageService.reachScore(0.0);
            double lastTenPercent = TriageService.reachScore(1.0) - TriageService.reachScore(0.9);

            assertThat(firstTenPercent).isGreaterThan(lastTenPercent);
        }

        @ParameterizedTest(name = "fraction {0} is rejected")
        @ValueSource(doubles = {-0.01, 1.01, Double.NaN})
        void rejectsFractionsOutsideZeroToOne(double fraction) {
            assertThatThrownBy(() -> TriageService.reachScore(fraction))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ------------------------------------------------------------------
    // The P0 floor rule
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("P0 floor rule")
    class P0Floor {

        @Test
        @DisplayName("a BLOCKER on the critical path is P0 even when it scores in P2")
        void floorsBlockerOnCriticalPathToP0() {
            // One user, seen once, never worked anyway — but it is a total failure of
            // authentication. Scores 56.55, which is P2. The floor rule overrides it.
            TriageResult result = service.triage(new TriageInput(
                    Severity.BLOCKER,
                    0.0,
                    Reproducibility.ONCE,
                    RegressionFlag.NEW_DEFECT,
                    ComponentCriticality.CRITICAL_PATH));

            assertThat(result.score()).isEqualTo(56.55);
            assertThat(Priority.fromScore(result.score())).isEqualTo(Priority.P2);
            assertThat(result.priority()).isEqualTo(Priority.P0);
            assertThat(result.flooredToP0()).isTrue();
        }

        @Test
        @DisplayName("the same BLOCKER on a CORE component is not floored")
        void doesNotFloorBlockerOnNonCriticalComponent() {
            // The control case for the test above. Without this pair, a bug that floored
            // everything to P0 would pass the previous test.
            TriageResult result = service.triage(new TriageInput(
                    Severity.BLOCKER,
                    0.0,
                    Reproducibility.ONCE,
                    RegressionFlag.NEW_DEFECT,
                    ComponentCriticality.CORE));

            assertThat(result.score()).isEqualTo(54.15);
            assertThat(result.priority()).isEqualTo(Priority.P2);
            assertThat(result.flooredToP0()).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // Documented model behaviour
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("model behaviour worth revisiting")
    class ModelBehaviour {

        @Test
        @DisplayName("a trivial defect affecting everyone currently lands in P2, not P0")
        void trivialDefectWithTotalReachLandsInP2() {
            // The classic severity-vs-priority example is a company-name typo on the
            // landing page: trivial severity, but every visitor sees it, so in practice
            // you ship the fix within the hour.
            //
            // Under the starting weights it scores 54.0 and buckets as P2. That is a
            // real limitation of the model, not a bug in this code — severity at 0.40
            // outweighs reach at 0.25, so nothing TRIVIAL can reach P0 on reach alone.
            //
            // This test documents where the model stands today. When you tune the
            // weights in phase 5 against real resolution data, this is the assertion
            // that will tell you what your change actually did.
            TriageResult result = service.triage(new TriageInput(
                    Severity.TRIVIAL,
                    1.0,
                    Reproducibility.ALWAYS,
                    RegressionFlag.NEW_DEFECT,
                    ComponentCriticality.PERIPHERAL));

            assertThat(result.score()).isEqualTo(54.0);
            assertThat(result.priority()).isEqualTo(Priority.P2);
        }

        @Test
        @DisplayName("a regression outranks an identical new defect")
        void regressionsOutrankNewDefects() {
            TriageInput asRegression = new TriageInput(
                    Severity.MAJOR, 0.3, Reproducibility.ALWAYS,
                    RegressionFlag.REGRESSION, ComponentCriticality.CORE);
            TriageInput asNewDefect = new TriageInput(
                    Severity.MAJOR, 0.3, Reproducibility.ALWAYS,
                    RegressionFlag.NEW_DEFECT, ComponentCriticality.CORE);

            assertThat(service.triage(asRegression).score())
                    .isGreaterThan(service.triage(asNewDefect).score());
        }
    }

    // ------------------------------------------------------------------
    // Input validation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("input validation")
    class InputValidation {

        @Test
        void rejectsNullInput() {
            assertThatThrownBy(() -> service.triage(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNullSeverity() {
            assertThatThrownBy(() -> new TriageInput(
                    null, 0.5, Reproducibility.ALWAYS,
                    RegressionFlag.NEW_DEFECT, ComponentCriticality.CORE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }

        @Test
        void rejectsAffectedFractionAboveOne() {
            assertThatThrownBy(() -> new TriageInput(
                    Severity.MAJOR, 1.5, Reproducibility.ALWAYS,
                    RegressionFlag.NEW_DEFECT, ComponentCriticality.CORE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("affectedUserFraction");
        }

        @Test
        void rejectsNullWeights() {
            assertThatThrownBy(() -> new TriageService(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ------------------------------------------------------------------
    // Weight invariant
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("weight invariant")
    class WeightInvariant {

        @Test
        void defaultWeightsSumToOne() {
            TriageWeights w = TriageWeights.DEFAULTS;
            assertThat(w.severity() + w.reach() + w.reproducibility()
                    + w.regression() + w.component())
                    .isCloseTo(1.0, within(1e-9));
        }

        @Test
        @DisplayName("weights that do not sum to 1.0 are rejected at construction")
        void rejectsWeightsThatDoNotSumToOne() {
            // Without this guard, scores drift outside 0-100 and every bucket boundary
            // silently moves. Catching it in the constructor means it can never happen
            // at runtime.
            assertThatThrownBy(() -> new TriageWeights(0.5, 0.5, 0.5, 0.5, 0.5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must sum to 1.0");
        }

        @Test
        void rejectsNegativeWeights() {
            assertThatThrownBy(() -> new TriageWeights(-0.1, 0.35, 0.25, 0.30, 0.20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("zero or positive");
        }

        @Test
        @DisplayName("a custom weighting changes the outcome, proving weights are actually used")
        void customWeightsChangeTheResult() {
            // Guards against the classic bug where a config value is read, stored, and
            // then never actually referenced in the calculation.
            TriageService reachDominant = new TriageService(
                    new TriageWeights(0.10, 0.70, 0.10, 0.05, 0.05));

            TriageInput widespreadTypo = new TriageInput(
                    Severity.TRIVIAL, 1.0, Reproducibility.ALWAYS,
                    RegressionFlag.NEW_DEFECT, ComponentCriticality.PERIPHERAL);

            assertThat(reachDominant.triage(widespreadTypo).score())
                    .isGreaterThan(service.triage(widespreadTypo).score());
        }
    }
}
