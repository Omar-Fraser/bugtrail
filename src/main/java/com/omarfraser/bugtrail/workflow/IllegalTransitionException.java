package com.omarfraser.bugtrail.workflow;

import com.omarfraser.bugtrail.domain.TicketStatus;

/**
 * Thrown when a caller attempts a status change the workflow does not allow.
 *
 * <p>A typed exception rather than a boolean return so the API layer can map it to
 * 409 Conflict with a message that names both states. "Cannot move from CLOSED to
 * IN_PROGRESS" tells a user what to do next; "false" does not.
 */
public class IllegalTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final TicketStatus from;
    private final TicketStatus to;

    public IllegalTransitionException(TicketStatus from, TicketStatus to) {
        super(buildMessage(from, to));
        this.from = from;
        this.to = to;
    }

    private static String buildMessage(TicketStatus from, TicketStatus to) {
        if (from != null && from.isTerminal()) {
            return "Cannot move a ticket out of terminal state " + from
                    + ". Terminal states are final; file a new ticket instead.";
        }
        return "Cannot move a ticket from " + from + " to " + to + ".";
    }

    public TicketStatus from() {
        return from;
    }

    public TicketStatus to() {
        return to;
    }
}
