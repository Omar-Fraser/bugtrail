package com.omarfraser.bugtrail.domain;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.OffsetDateTime;
import jakarta.persistence.Table;


/**
 * A reported defect, and the record of everything that happened to it.
 *
 * <p>The central entity of BugTrail. Two things about it are worth knowing before
 * reading the fields:
 *
 * <p><b>Severity and priority are separate.</b> Severity describes the defect and
 * is set by the reporter. Priority describes the schedule and is computed by
 * {@code TriageService}. They routinely disagree, and collapsing them into one
 * field is the most common modelling mistake in home-grown bug trackers.
 *
 * <p><b>Status changes go through {@code TransitionValidator}.</b> Setting the
 * status field directly bypasses the workflow rules, so the only supported route
 * is the service layer.
 */
@Entity
@Table(name = "ticket")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable key, e.g. {@code BT-142}. Assigned once never reused,
     * even if the ticket is deleted.
     * 
     * <p>This is what people say out loud, paste into commity messages, and search
     * for. The numeric id that exists for the database; this exists for humans.
     */
    @Column(name = "reference", nullable = false, unique = true, length = 24)
    private String reference;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /**
     * The component the defect is in. Nullable, because a reporter often does not
     * know -- "the app crashed" does not tell you whether it was auth or the board.
     * Triage assigns it, which is also when it starts affecting the priority score.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
        private Component component;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    /**
     * Numbered steps that reproduce the defect. Nullable, but a report without
     * them is usually the one that gets bounced back -- "it's broken" is not
     * actionable, and the round trip to ask costs more than writing them did.
     */
    @Column(name = "steps_to_reproduce", columnDefinition = "text")
    private String stepsToReproduce;

    /**
     * Browser, OS, app version, device. What distinguishes "it's broken" from
     * "it's broken on Safari 17".
     */
    @Column(name = "environment", length = 255)
    private String environment;

    /**
     * Current position in the workflow. 
     * 
     * <p>Defaults to NEW, mirroring the column default. There is deliberately no
     * public setter -- see {@code transitionTo} further down. Every change has to
     * pass {@code TransitionValidator}, and a setter would be a way around it.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TicketStatus status = TicketStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "reproducibility", nullable = false, length = 16)
    private Reproducibility reproducibility;

    /**
     * Whether this worked in a previous release. Weighted heavily in triage --
     * a regression means the test suite failed to catch something it should have.
     */
    @Column(name = "is_regression", nullable = false)
    private boolean isRegression = false;

    /**
     * Proportion of users affected, 0.0000 to 1.0000.
     *
     * <p>BigDecimal, not double, because the column is NUMERIC(5,4) -- an exact
     * decimal type. Starts at zero: nobody knows the reach of a defect when it
     * is first filed.
     */
    @Column(name = "affected_user_fraction", nullable = false, precision = 5, scale = 4)
    private BigDecimal affectedUserFraction = BigDecimal.ZERO;
    /**
     * Computed by {@code TriageService}, never set by the reporter.
     *
     * <p>Nullable because a ticket has no priority until it is triaged -- NEW
     * tickets carry a null here, and that is meaningful rather than missing data.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 4)
    private Priority priority;

     /**
     * The raw weighted score behind the priority bucket, 0.00 to 100.00.
     *
     * <p>Stored alongside the bucket so triage decisions can be explained after
     * the fact -- "why is this P1" is answerable with a number, and re-tuning the
     * weights later can be evaluated against historical scores.
     */
    @Column(name = "triage_score", precision = 5, scale = 2)
    private BigDecimal triageScore;

    /**
     * True when the P0 floor rule overrode the computed bucket: a BLOCKER on a 
     * CRITICAL_PATH component is always P0, whatever the arithmetic said,
     * 
     * <p>Surfaced rather than hidden so the UI can explain to a developer why a 
     * mid-scoring ticket is setting at the top of their queue.
     */
    @Column(name = "floored_to_p0", nullable = false)
    private boolean flooredToP0 = false;

    /**
     * A QA lead's manual override of the computed priority. 
     * 
     * <p>Null in the normal case. When set, all four override fields below must be 
     * populated together -- the {@code ticket_override_needs_reason} constraint in
     * V1__init.sql enforces it at database level, so a partial override cannot
     * be written even by a buggy service or a manual UPDATE.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority_override", length = 4)
    private Priority priorityOverride;

    /**
     * Why the computed priority was wrong. Required whenever an override is set. 
     * 
     * <p>This is what makes the override auditable rather than arbitrary. "P2 but
     * the customer escalated to the CEO" is a reason; silence is not.
     */
    @Column(name = "priority_override_reason", columnDefinition = "text")
    private String priorityOverrideReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_override_by")
    private AppUser priorityOverrideBy;

    @Column(name = "priority_override_at")
    private OffsetDateTime priorityOverrideAt;

    /**
     * Who filed it. Never null, never changes -- the reporter is a historical fact
     * about the ticket, and reassigning it would falsify the record.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private AppUser reporter;

    /**
     * Who is working on it. Null until the ticket is assigned, and null again if
     * it goes back to the triage queue.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    /**
     * The ticket this one duplicates, when status is DUPLICATE.
     *
     * <p>A self-reference: Ticket pointing at Ticket. The link is what lets the
     * board answer "how many people hit this", which then feeds the reach input on
     * the original's triage score -- five duplicates means five times the evidence
     * that it matters.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_id")
    private Ticket duplicateOf;

    /**
     * How many times this ticket has been reopened.
     *
     * <p>A quality signal in its own right. A ticket reopened three times means
     * the fix was wrong twice, and that pattern is worth surfacing in the phase 5
     * analytics -- reopen rate says more about a team than raw close counts do.
     */
    @Column(name = "reopen_count", nullable = false)
    private int reopenCount = 0;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    /**
     * Last modification. Maintained by {@code touch()} rather than by the
     * database, so every write path goes through one place.
     */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    /**
     * When the ticket reached CLOSED. Null while open, and null again if it is
     * reopened.
     *
     * <p>This is the field mean-time-to-resolution is calculated from in phase 5:
     * {@code closedAt - createdAt}, bucketed by priority.
     */
    @Column(name = "closed_at")
    private OffsetDateTime closedAt;
    
}