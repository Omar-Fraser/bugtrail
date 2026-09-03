package com.omarfraser.bugtrail.workflow;

import com.omarfraser.bugtrail.domain.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Exhaustive coverage of the transition matrix.
 *
 * <p>The important structural decision: the expected legal set below is written out
 * independently rather than read from {@link TransitionValidator}. Deriving the
 * expectation from the implementation would produce a test that passes no matter
 * what the implementation says — a tautology with a green checkmark.
 *
 * <p>With ten states there are 100 ordered pairs. Fourteen are legal. This class
 * asserts all 100, which is the difference between "I tested the happy path" and
 * "no illegal transition can slip through".
 */
class TransitionValidatorTest {

    private final TransitionValidator validator = new TransitionValidator();

    /** Every legal transition, written out by hand from the workflow design. */
    private static final Set<String> EXPECTED_LEGAL = new LinkedHashSet<>(List.of(
            "NEW>TRIAGED",

            "TRIAGED>ASSIGNED",
            "TRIAGED>REJECTED",
            "TRIAGED>DUPLICATE",
            "TRIAGED>WONT_FIX",

            "ASSIGNED>IN_PROGRESS",
            "ASSIGNED>TRIAGED",

            "IN_PROGRESS>IN_REVIEW",
            "IN_PROGRESS>ASSIGNED",

            "IN_REVIEW>VERIFIED",
            "IN_REVIEW>IN_PROGRESS",

            "VERIFIED>CLOSED",
            "VERIFIED>IN_PROGRESS",

            "CLOSED>TRIAGED"
    ));

    private static String key(TicketStatus from, TicketStatus to) {
        return from.name() + ">" + to.name();
    }

    /** All 100 ordered pairs of states. */
    private static Stream<Object[]> allPairs() {
        List<Object[]> pairs = new ArrayList<>();
        for (TicketStatus from : TicketStatus.values()) {
            for (TicketStatus to : TicketStatus.values()) {
                pairs.add(new Object[]{from, to});
            }
        }
        return pairs.stream();
    }

    // ------------------------------------------------------------------
    // The full matrix
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    @DisplayName("every one of the 100 state pairs behaves as the design says")
    void everyPairMatchesTheDesign(TicketStatus from, TicketStatus to) {
        boolean shouldBeLegal = EXPECTED_LEGAL.contains(key(from, to));

        assertThat(validator.isLegal(from, to))
                .as("%s -> %s should be %s", from, to, shouldBeLegal ? "legal" : "illegal")
                .isEqualTo(shouldBeLegal);

        if (shouldBeLegal) {
            assertDoesNotThrow(() -> validator.requireLegal(from, to));
        } else {
            assertThatThrownBy(() -> validator.requireLegal(from, to))
                    .isInstanceOf(IllegalTransitionException.class);
        }
    }

    @Test
    @DisplayName("the matrix contains exactly 14 legal transitions")
    void theMatrixHasTheExpectedSize() {
        // A canary. If someone adds a transition without updating this test, the count
        // changes and this fails — which is the prompt to think about whether the new
        // rule was intended.
        long legalCount = allPairs()
                .filter(p -> validator.isLegal((TicketStatus) p[0], (TicketStatus) p[1]))
                .count();

        assertThat(legalCount).isEqualTo(14);
    }

    // ------------------------------------------------------------------
    // Named rules
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("workflow rules")
    class WorkflowRules {

        @Test
        @DisplayName("a ticket cannot go straight from NEW to ASSIGNED without triage")
        void cannotSkipTriage() {
            assertThat(validator.isLegal(TicketStatus.NEW, TicketStatus.ASSIGNED)).isFalse();
        }

        @Test
        @DisplayName("reopening a closed ticket sends it back to TRIAGED, not IN_PROGRESS")
        void reopeningResetsTriage() {
            assertThat(validator.isLegal(TicketStatus.CLOSED, TicketStatus.TRIAGED)).isTrue();
            assertThat(validator.isLegal(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS)).isFalse();
        }

        @ParameterizedTest(name = "{0} cannot be rejected before triage")
        @CsvSource({
                "NEW, REJECTED",
                "NEW, DUPLICATE",
                "NEW, WONT_FIX",
                "ASSIGNED, REJECTED",
                "IN_PROGRESS, DUPLICATE",
                "VERIFIED, WONT_FIX"
        })
        void terminalOutcomesAreReachableOnlyFromTriaged(TicketStatus from, TicketStatus to) {
            assertThat(validator.isLegal(from, to)).isFalse();
        }

        @Test
        @DisplayName("a ticket can never transition to itself")
        void selfTransitionsAreAlwaysIllegal() {
            for (TicketStatus status : TicketStatus.values()) {
                assertThat(validator.isLegal(status, status))
                        .as("%s -> %s", status, status)
                        .isFalse();
            }
        }
    }

    // ------------------------------------------------------------------
    // Terminal states
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("terminal states")
    class TerminalStates {

        @ParameterizedTest
        @EnumSource(value = TicketStatus.class, names = {"REJECTED", "DUPLICATE", "WONT_FIX"})
        void haveNoLegalTargets(TicketStatus terminal) {
            assertThat(terminal.isTerminal()).isTrue();
            assertThat(validator.legalTargets(terminal)).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = TicketStatus.class, names = {"REJECTED", "DUPLICATE", "WONT_FIX"})
        void produceAHelpfulErrorMessage(TicketStatus terminal) {
            // The message has to tell the user what to do instead, not just say no.
            assertThatThrownBy(() -> validator.requireLegal(terminal, TicketStatus.TRIAGED))
                    .isInstanceOf(IllegalTransitionException.class)
                    .hasMessageContaining("terminal")
                    .hasMessageContaining("file a new ticket");
        }

        @Test
        @DisplayName("CLOSED is not terminal, because tickets get reopened")
        void closedIsNotTerminal() {
            assertThat(TicketStatus.CLOSED.isTerminal()).isFalse();
            assertThat(validator.legalTargets(TicketStatus.CLOSED)).isNotEmpty();
        }
    }

    // ------------------------------------------------------------------
    // Null handling and the UI helper
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("null handling")
    class NullHandling {

        @Test
        void nullsAreNeverLegal() {
            assertThat(validator.isLegal(null, TicketStatus.TRIAGED)).isFalse();
            assertThat(validator.isLegal(TicketStatus.NEW, null)).isFalse();
            assertThat(validator.isLegal(null, null)).isFalse();
        }

        @Test
        void legalTargetsOfNullIsEmptyRatherThanAnException() {
            assertThat(validator.legalTargets(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("legalTargets")
    class LegalTargets {

        @Test
        @DisplayName("returns exactly the states the UI should offer")
        void returnsTheReachableStates() {
            assertThat(validator.legalTargets(TicketStatus.TRIAGED))
                    .containsExactlyInAnyOrder(
                            TicketStatus.ASSIGNED,
                            TicketStatus.REJECTED,
                            TicketStatus.DUPLICATE,
                            TicketStatus.WONT_FIX);
        }

        @Test
        @DisplayName("the returned set cannot be modified by a caller")
        void isUnmodifiable() {
            // The map is shared static state. If a caller could mutate what comes back,
            // one buggy controller would corrupt the workflow for the whole application.
            Set<TicketStatus> targets = validator.legalTargets(TicketStatus.NEW);

            assertThatThrownBy(() -> targets.add(TicketStatus.CLOSED))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
