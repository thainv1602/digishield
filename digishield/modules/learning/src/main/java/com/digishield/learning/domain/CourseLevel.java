package com.digishield.learning.domain;

/**
 * Level of a course.
 */
public enum CourseLevel {
    /** Basic. */
    BASIC,
    /** Beginner. */
    BEGINNER,
    /** Intermediate. */
    INTERMEDIATE,
    /** Advanced. */
    ADVANCED;

    /**
     * Position on the ladder, lowest first, used to pitch remediation at how
     * often someone has clicked before.
     * <p>
     * Spelled out rather than left to {@code ordinal()}: reordering the
     * constants would otherwise silently change which course a repeat offender
     * is given.
     */
    public int rank() {
        return switch (this) {
            case BASIC -> 0;
            case BEGINNER -> 1;
            case INTERMEDIATE -> 2;
            case ADVANCED -> 3;
        };
    }
}
