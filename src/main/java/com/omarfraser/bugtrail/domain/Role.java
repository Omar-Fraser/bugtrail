package com.omarfraser.bugtrail.domain;

/**
 * What a user is permitted to do in BugTrail.
 * 
 * <p>These names are duplicated in the {@code app_user_role_valid} check constraint 
 * in {@code V1__init.sql}. Adding a role here without a matching Flyway migration
 * will fail at insert time, not compile time -- so change them both together.
 */
public enum Role {
    /** Can file tickets and comment. Cannot triage, assign, or close. */
    REPORTER,

    /** Can be assigned tickets and move them through work states. */
    DEVELOPER,

    /** Can Triage, override computed priority , and verify bug fixes. */
    QA_LEAD,

    /** Full access, including user management. */
    ADMIN
}
