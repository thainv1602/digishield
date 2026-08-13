package com.digishield.reporting.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

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
    DISMISSED;

    /**
     * Statuses that still need an analyst's attention, and so count towards the
     * dashboard's open-alert tile.
     *
     * <p>Kept here rather than spelled out at the call site: the set was
     * previously a pair of string comparisons in the boot module, where a
     * status added to this enum would have been silently treated as closed.
     */
    private static final Set<ReportStatus> OPEN = Collections.unmodifiableSet(
            EnumSet.of(SUBMITTED, TRIAGING));

    /** @return the statuses that count as still open, for use in queries. */
    public static Set<ReportStatus> openStatuses() {
        return OPEN;
    }

    /** @return true when a report in this status is still awaiting triage. */
    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
