package com.omarfraser.bugtrail.domain;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behaviour of {@link Ticket}: what the constructor wires up, and what a newly
 * filed ticket looks like before anyone has touched it.
 *
 * <p>Every value below is deliberately distinct. If two fields held the same
 * string, a getter returning the wrong one would still pass -- and a getter
 * returning the wrong field is invisible to the compiler and to the database.
 * A test like this one is the only thing that catches it.
 */
class TicketTest {

    private Project sampleProject() {
        return new Project("BT", "BugTrail", "Defect tracking with computed triage");
    }

    private AppUser sampleReporter() {
        return new AppUser(
                "ofraser",
                "omar@example.com",
                "Omar Fraser",
                "$2a$10$NOTAREALHASH0123456789",
                Role.REPORTER);
    }

    /**
     * A valid ticket for tests that do not care about the specific values.
     */
    private Ticket sampleTicket() {
        return new Ticket(
                "BT-142",
                sampleProject(),
                "Export crashes on empty date range",
                "Clicking Export with no dates set throws an NPE in ReportExporter.",
                Severity.MAJOR,
                Reproducibility.ALWAYS,
                sampleReporter());
    }

    @Test
    @DisplayName("the constructor assigns each argument to its own field")
    void constructorWiresEveryArgumentToTheCorrectField() {
        Project project = sampleProject();
        AppUser reporter = sampleReporter();

        Ticket ticket = new Ticket(
                "BT-142",
                project,
                "Export crashes on empty date range",
                "Clicking Export with no dates set throws an NPE in ReportExporter.",
                Severity.MAJOR,
                Reproducibility.ALWAYS,
                reporter);

        assertThat(ticket.getReference()).isEqualTo("BT-142");
        assertThat(ticket.getProject()).isSameAs(project);
        assertThat(ticket.getTitle()).isEqualTo("Export crashes on empty date range");
        assertThat(ticket.getDescription())
                .isEqualTo("Clicking Export with no dates set throws an NPE in ReportExporter.");
        assertThat(ticket.getSeverity()).isEqualTo(Severity.MAJOR);
        assertThat(ticket.getReproducibility()).isEqualTo(Reproducibility.ALWAYS);
        assertThat(ticket.getReporter()).isSameAs(reporter);
    }

    @Test
    @DisplayName("a newly filed ticket is NEW, untriaged, unassigned and never closed")
    void newTicketHasTheRightDefaults() {
        Ticket ticket = sampleTicket();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);

        // Untriaged. Null here is meaningful data, not missing data: it is how
        // the board tells "nobody has looked at this" from "looked at, low".
        assertThat(ticket.getPriority()).isNull();
        assertThat(ticket.getTriageScore()).isNull();
        assertThat(ticket.isFlooredToP0()).isFalse();

        assertThat(ticket.isRegression()).isFalse();
        assertThat(ticket.getAffectedUserFraction()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ticket.getReopenCount()).isZero();

        assertThat(ticket.getComponent()).isNull();
        assertThat(ticket.getAssignee()).isNull();
        assertThat(ticket.getDuplicateOf()).isNull();
        assertThat(ticket.getClosedAt()).isNull();

        assertThat(ticket.getCreatedAt()).isNotNull();
        assertThat(ticket.getUpdatedAt()).isNotNull();
    }
}
