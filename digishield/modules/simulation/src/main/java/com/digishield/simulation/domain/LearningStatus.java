package com.digishield.simulation.domain;

/**
 * Remediation learning status for a user caught by a simulation.
 */
public enum LearningStatus {
    /** No remediation required (e.g. the user reported or ignored). */
    NONE,
    /** Auto-enrolled and currently studying. */
    IN_PROGRESS,
    /** Remediation course completed. */
    COMPLETED
}
