package com.omarfraser.bugtrail.workflow;

import com.omarfraser.bugtrail.domain.TicketStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for which status changes the workflow permits.
 *
 * <p>Every rule lives in one map, which is what makes the whole thing testable by
 * enumeration: iterate all 100 ordered pairs of states, assert that the legal ones
 * pass and every other one throws. That test is in {@code TransitionValidatorTest}
 * and it is the reason a dropped rule cannot slip through unnoticed.
 *
 * <p>Two design decisions worth defending in an interview:
 * <ul>
 *   <li>Terminal outcomes (REJECTED, DUPLICATE, WONT_FIX) are reachable only from
 *       TRIAGED. You cannot reject a ticket nobody has looked at.</li>
 *   <li>CLOSED returns to TRIAGED rather than to IN_PROGRESS. Reopening resets
 *       triage, because the conditions that set the original priority — reach,
 *       regression status — have almost certainly changed by then.</li>
 * </ul>
 */
public class TransitionValidator {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = buildTransitions();

    private static Map<TicketStatus, Set<TicketStatus>> buildTransitions() {
        Map<TicketStatus, Set<TicketStatus>> map = new EnumMap<>(TicketStatus.class);

        map.put(TicketStatus.NEW, EnumSet.of(
                TicketStatus.TRIAGED));

        map.put(TicketStatus.TRIAGED, EnumSet.of(
                TicketStatus.ASSIGNED,
                TicketStatus.REJECTED,
                TicketStatus.DUPLICATE,
                TicketStatus.WONT_FIX));

        // Back to TRIAGED covers unassignment and re-scoring.
        map.put(TicketStatus.ASSIGNED, EnumSet.of(
                TicketStatus.IN_PROGRESS,
                TicketStatus.TRIAGED));

        // Back to ASSIGNED covers a developer handing the ticket off.
        map.put(TicketStatus.IN_PROGRESS, EnumSet.of(
                TicketStatus.IN_REVIEW,
                TicketStatus.ASSIGNED));

        // Back to IN_PROGRESS covers a rejected code review.
        map.put(TicketStatus.IN_REVIEW, EnumSet.of(
                TicketStatus.VERIFIED,
                TicketStatus.IN_PROGRESS));

        // Back to IN_PROGRESS covers QA finding the fix incomplete.
        map.put(TicketStatus.VERIFIED, EnumSet.of(
                TicketStatus.CLOSED,
                TicketStatus.IN_PROGRESS));

        // Reopening. Resets triage rather than resuming work.
        map.put(TicketStatus.CLOSED, EnumSet.of(
                TicketStatus.TRIAGED));

        map.put(TicketStatus.REJECTED, EnumSet.noneOf(TicketStatus.class));
        map.put(TicketStatus.DUPLICATE, EnumSet.noneOf(TicketStatus.class));
        map.put(TicketStatus.WONT_FIX, EnumSet.noneOf(TicketStatus.class));

        return Collections.unmodifiableMap(map);
    }

    /**
     * True if moving from {@code from} to {@code to} is permitted.
     *
     * <p>A transition to the same state is never legal — "closing a closed ticket"
     * is a no-op the caller almost certainly did not intend, and letting it through
     * would put a meaningless entry in the audit log.
     */
    public boolean isLegal(TicketStatus from, TicketStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Enforces the transition, throwing if it is not permitted.
     *
     * @throws IllegalTransitionException if the move is not allowed
     */
    public void requireLegal(TicketStatus from, TicketStatus to) {
        if (!isLegal(from, to)) {
            throw new IllegalTransitionException(from, to);
        }
    }

    /** The states reachable from {@code from}, for rendering the UI's available actions. */
    public Set<TicketStatus> legalTargets(TicketStatus from) {
        if (from == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(ALLOWED.getOrDefault(from, Set.of()));
    }
}
