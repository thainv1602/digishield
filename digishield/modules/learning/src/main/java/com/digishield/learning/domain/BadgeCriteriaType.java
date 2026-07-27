package com.digishield.learning.domain;

/**
 * What a badge measures.
 * <p>
 * Deliberately short: every type here is something the platform already records,
 * so a badge can only be defined in terms of evidence that exists. Adding a type
 * means finding the measure first — which is the step that was missing when the
 * catalogue described conditions in prose that nothing could evaluate.
 */
public enum BadgeCriteriaType {

    /** Courses the learner has passed. */
    COURSES_COMPLETED,
    /** Phishing reports of theirs that triage confirmed as genuine. */
    REPORTS_CONFIRMED,
    /** Points on their gamification profile. */
    POINTS
}
