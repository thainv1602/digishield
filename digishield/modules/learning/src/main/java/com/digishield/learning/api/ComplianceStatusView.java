package com.digishield.learning.api;

/**
 * Aggregated compliance status (Compliance screen KPI tiles).
 *
 * @param compliantPct    overall compliant percentage (0..100)
 * @param compliantCount  number of compliant users
 * @param totalCount      total number of users in scope
 * @param overdueCount    number of overdue users
 * @param policyCount     total number of policies
 * @param completedCount  number of fully completed policies
 * @param dueSoonCount    number of policies due soon
 */
public record ComplianceStatusView(double compliantPct, int compliantCount, int totalCount,
                                   int overdueCount, int policyCount, int completedCount,
                                   int dueSoonCount) {
}
