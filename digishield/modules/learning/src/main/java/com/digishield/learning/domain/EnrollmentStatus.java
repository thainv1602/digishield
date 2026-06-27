package com.digishield.learning.domain;

/**
 * Status of a course enrollment.
 */
public enum EnrollmentStatus {
    /** Assigned but not yet started. */
    ASSIGNED,
    /** In progress. */
    IN_PROGRESS,
    /** Completed. */
    COMPLETED,
    /** Overdue. */
    OVERDUE
}
