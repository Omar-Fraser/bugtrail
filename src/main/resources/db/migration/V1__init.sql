-- BugTrail V1 — core schema
--
-- The decision this schema exists to encode: severity and priority are SEPARATE
-- columns. Severity describes the defect and is set by the reporter. Priority
-- describes the schedule and is computed by TriageService. They routinely disagree,
-- and collapsing them into one column is the most common modelling mistake in
-- home-grown bug trackers.

-- ---------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------
CREATE TABLE app_user (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    display_name  VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT app_user_role_valid
        CHECK (role IN ('REPORTER', 'DEVELOPER', 'QA_LEAD', 'ADMIN'))
);

-- ---------------------------------------------------------------------------
-- Projects and components
-- ---------------------------------------------------------------------------
CREATE TABLE project (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 'key' is legal in Postgres but fights with JPA and several SQL tools.
    -- Naming it project_key costs nothing and avoids a class of confusing errors.
    project_key VARCHAR(16)  NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE component (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id  BIGINT       NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name        VARCHAR(128) NOT NULL,
    criticality VARCHAR(16)  NOT NULL,

    CONSTRAINT component_criticality_valid
        CHECK (criticality IN ('CRITICAL_PATH', 'CORE', 'PERIPHERAL')),
    CONSTRAINT component_unique_per_project UNIQUE (project_id, name)
);

-- ---------------------------------------------------------------------------
-- Tickets
-- ---------------------------------------------------------------------------
CREATE TABLE ticket (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference         VARCHAR(24)  NOT NULL UNIQUE,
    project_id        BIGINT       NOT NULL REFERENCES project (id),
    component_id      BIGINT       REFERENCES component (id),

    title             VARCHAR(255) NOT NULL,
    description       TEXT         NOT NULL,
    steps_to_reproduce TEXT,
    environment       VARCHAR(255),

    status            VARCHAR(16)  NOT NULL DEFAULT 'NEW',

    -- Reporter-set: how badly the software misbehaves.
    severity          VARCHAR(16)  NOT NULL,
    reproducibility   VARCHAR(16)  NOT NULL,
    is_regression     BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Proportion of users affected, 0.0 to 1.0. Feeds the log-scaled reach score.
    affected_user_fraction NUMERIC(5, 4) NOT NULL DEFAULT 0.0,

    -- System-computed: how urgently it should be fixed. NULL until triage runs.
    priority          VARCHAR(4),
    triage_score      NUMERIC(5, 2),
    floored_to_p0     BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Set when a QA lead overrides the computed priority. The justification is
    -- NOT NULL-checked against the override below: you cannot override silently.
    priority_override         VARCHAR(4),
    priority_override_reason  TEXT,
    priority_override_by      BIGINT REFERENCES app_user (id),
    priority_override_at      TIMESTAMPTZ,

    reporter_id       BIGINT       NOT NULL REFERENCES app_user (id),
    assignee_id       BIGINT       REFERENCES app_user (id),

    duplicate_of_id   BIGINT       REFERENCES ticket (id),
    reopen_count      INTEGER      NOT NULL DEFAULT 0,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    closed_at         TIMESTAMPTZ,

    CONSTRAINT ticket_status_valid CHECK (status IN (
        'NEW', 'TRIAGED', 'ASSIGNED', 'IN_PROGRESS', 'IN_REVIEW',
        'VERIFIED', 'CLOSED', 'REJECTED', 'DUPLICATE', 'WONT_FIX')),

    CONSTRAINT ticket_severity_valid CHECK (severity IN (
        'BLOCKER', 'CRITICAL', 'MAJOR', 'MINOR', 'TRIVIAL')),

    CONSTRAINT ticket_reproducibility_valid CHECK (reproducibility IN (
        'ALWAYS', 'INTERMITTENT', 'ONCE')),

    CONSTRAINT ticket_priority_valid CHECK (
        priority IS NULL OR priority IN ('P0', 'P1', 'P2', 'P3')),

    CONSTRAINT ticket_override_priority_valid CHECK (
        priority_override IS NULL OR priority_override IN ('P0', 'P1', 'P2', 'P3')),

    CONSTRAINT ticket_fraction_in_range CHECK (
        affected_user_fraction >= 0 AND affected_user_fraction <= 1),

    -- An override without a stated reason is not an override, it is a mystery.
    CONSTRAINT ticket_override_needs_reason CHECK (
        priority_override IS NULL
        OR (priority_override_reason IS NOT NULL
            AND priority_override_by IS NOT NULL
            AND priority_override_at IS NOT NULL)),

    -- DUPLICATE status and a duplicate_of link imply each other.
    CONSTRAINT ticket_duplicate_link_consistent CHECK (
        (status = 'DUPLICATE' AND duplicate_of_id IS NOT NULL)
        OR (status <> 'DUPLICATE' AND duplicate_of_id IS NULL)),

    CONSTRAINT ticket_not_duplicate_of_itself CHECK (duplicate_of_id <> id)
);

CREATE INDEX idx_ticket_status        ON ticket (status);
CREATE INDEX idx_ticket_priority      ON ticket (priority);
CREATE INDEX idx_ticket_assignee      ON ticket (assignee_id);
CREATE INDEX idx_ticket_project       ON ticket (project_id);
CREATE INDEX idx_ticket_component     ON ticket (component_id);
CREATE INDEX idx_ticket_duplicate_of  ON ticket (duplicate_of_id);
CREATE INDEX idx_ticket_created_at    ON ticket (created_at DESC);

-- The board's default query: open tickets ordered by urgency. Without this index
-- it is a sequential scan on every page load.
CREATE INDEX idx_ticket_open_by_priority
    ON ticket (priority, created_at DESC)
    WHERE status NOT IN ('CLOSED', 'REJECTED', 'DUPLICATE', 'WONT_FIX');

-- ---------------------------------------------------------------------------
-- Comments
-- ---------------------------------------------------------------------------
CREATE TABLE ticket_comment (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    author_id  BIGINT      NOT NULL REFERENCES app_user (id),
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comment_ticket ON ticket_comment (ticket_id, created_at);

-- ---------------------------------------------------------------------------
-- Status history
-- ---------------------------------------------------------------------------
-- Every transition, appended. This is what the ticket timeline renders from, and
-- what mean-time-to-resolution is calculated from in phase 5.
CREATE TABLE status_change (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id   BIGINT      NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    from_status VARCHAR(16),
    to_status   VARCHAR(16) NOT NULL,
    changed_by  BIGINT      NOT NULL REFERENCES app_user (id),
    note        TEXT,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_status_change_ticket ON status_change (ticket_id, changed_at);

-- ---------------------------------------------------------------------------
-- Audit log
-- ---------------------------------------------------------------------------
-- Append-only by convention now, enforced by a revoke in phase 3 when roles exist.
-- Priority overrides, permission changes and deletions all land here.
CREATE TABLE audit_log (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id    BIGINT      REFERENCES app_user (id),
    action      VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id   BIGINT,
    detail      JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity     ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_occurred   ON audit_log (occurred_at DESC);
