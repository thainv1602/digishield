package com.digishield;

import com.digishield.analytics.api.DashboardMetricsProvider;
import com.digishield.learning.api.LearningService;
import com.digishield.reporting.api.ReportingService;
import com.digishield.reporting.api.dto.PhishingReportDto;
import com.digishield.shared.tenantcontext.TenantContext;
import org.springframework.stereotype.Component;

/**
 * Wires the analytics module's {@link DashboardMetricsProvider} SPI to the
 * learning and reporting modules, so the admin dashboard's training-completion
 * and open-alert tiles are data-driven instead of hardcoded. Lives in the boot
 * app to keep analytics decoupled (mirrors {@link ReportingRecentReports}).
 */
@Component
class DashboardMetricsBridge implements DashboardMetricsProvider {

    private final LearningService learningService;
    private final ReportingService reportingService;

    DashboardMetricsBridge(LearningService learningService, ReportingService reportingService) {
        this.learningService = learningService;
        this.reportingService = reportingService;
    }

    @Override
    public int trainingCompletionPct() {
        // Counted in the database by the learning module. This used to load
        // every enrollment the tenant had — and every course, so the views it
        // built could be labelled — to arrive at a single percentage.
        return learningService.completionPct(TenantContext.requireUuid());
    }

    @Override
    public OpenAlertCounts openAlerts() {
        int critical = 0;
        int warning = 0;
        // listReports(null) returns all reports; count the still-open ones by
        // AI severity (threat -> critical, spam -> warning).
        for (PhishingReportDto r : reportingService.listReports(null)) {
            String status = r.status() == null ? "" : r.status().toLowerCase();
            boolean open = status.equals("submitted") || status.equals("triaging");
            if (!open) {
                continue;
            }
            String label = r.aiLabel() == null ? "" : r.aiLabel().toLowerCase();
            if (label.equals("threat")) {
                critical++;
            } else if (label.equals("spam")) {
                warning++;
            }
        }
        return new OpenAlertCounts(critical + warning, critical, warning);
    }
}
