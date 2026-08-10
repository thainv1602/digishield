package com.digishield.reporting.domain;

/**
 * Processing status of a phishing report.
 *
 * <p>Persisted by name ({@code @Enumerated(EnumType.STRING)}), so the order of
 * the constants carries no meaning and new ones may be added safely.
 */
public enum ReportStatus {
    SUBMITTED,
    TRIAGING,
    CONFIRMED,
    /**
     * Judged a threat, but held rather than confirmed: the analyst wants it out
     * of the queue and out of users' reach while it is escalated. No reward or
     * risk-score movement follows, because the verdict is not final.
     */
    QUARANTINED,
    DISMISSED
}
