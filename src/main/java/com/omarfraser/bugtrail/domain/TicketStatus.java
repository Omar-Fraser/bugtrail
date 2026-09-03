package com.omarfraser.bugtrail.domain;

/**
 * The ten states a ticket can occupy.
 *
 * <p>Which moves between them are legal lives in
 * {@code com.omarfraser.bugtrail.workflow.TransitionValidator}, deliberately kept
 * separate so the rules can be tested without constructing tickets.
 */
public enum TicketStatus {

    /** Filed, not yet looked at. */
    NEW,

    /** Scored and bucketed. The only state terminal outcomes can be reached from. */
    TRIAGED,

    /** Has an owner, work not started. */
    ASSIGNED,

    /** Being worked on. */
    IN_PROGRESS,

    /** A fix exists and is awaiting code review. Entered automatically by the GitHub webhook. */
    IN_REVIEW,

    /** QA has confirmed the fix actually resolves the defect. */
    VERIFIED,

    /** Done. Can still be reopened, which sends it back to TRIAGED. */
    CLOSED,

    /** Not a defect — working as designed, or unreproducible. Terminal. */
    REJECTED,

    /** Already tracked by another ticket. Terminal. */
    DUPLICATE,

    /** A real defect the team has decided not to fix. Terminal. */
    WONT_FIX;

    /** True if no transition out of this state exists. */
    public boolean isTerminal() {
        return this == REJECTED || this == DUPLICATE || this == WONT_FIX;
    }
}
