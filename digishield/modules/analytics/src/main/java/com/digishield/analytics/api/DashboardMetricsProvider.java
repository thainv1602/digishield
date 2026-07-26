package com.digishield.analytics.api;

/**
 * SPI for cross-module dashboard metrics the analytics module can't compute from
 * its own risk repositories: training completion (learning module) and open
 * alerts (reporting module). The boot app bridges this to those modules, keeping
 * analytics decoupled (mirrors {@link RecentReportsProvider}).
 */
public interface DashboardMetricsProvider {

    /**
     * Percentage of the tenant's enrollments that are completed (0–100), or 0
     * when there are no enrollments.
     */
    int trainingCompletionPct();

    /**
     * Counts of the tenant's currently-open phishing alerts (untriaged reports),
     * split by AI severity.
     */
    OpenAlertCounts openAlerts();

    /**
     * Open-alert counts.
     *
     * @param total    total open alerts (critical + warning)
     * @param critical open reports the AI labelled as a threat
     * @param warning  open reports the AI labelled as spam
     */
    record OpenAlertCounts(int total, int critical, int warning) {
    }
}
