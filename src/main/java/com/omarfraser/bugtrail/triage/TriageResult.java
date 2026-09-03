package com.omarfraser.bugtrail.triage;

import com.omarfraser.bugtrail.domain.Priority;

/**
 * The outcome of scoring a defect.
 *
 * @param score        weighted triage score, 0–100, rounded to two decimals
 * @param priority     the bucket the ticket lands in
 * @param flooredToP0  true when the P0 floor rule overrode the computed bucket.
 *                     Surfaced rather than hidden so the UI can explain to a
 *                     developer why a mid-scoring ticket is sitting at the top of
 *                     their queue.
 */
public record TriageResult(double score, Priority priority, boolean flooredToP0) {
}
