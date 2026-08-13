package com.digishield;

import com.digishield.analytics.api.DashboardMetricsProvider;
import com.digishield.learning.api.LearningService;
import com.digishield.reporting.api.ReportingService;
import com.digishield.reporting.api.dto.OpenReportCountsDto;
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
        // The reporting module aggregates this in the database. It used to be a
        // scan of every report the tenant had ever filed, each mapped to a DTO
        // and counted here, on every dashboard load.
        OpenReportCountsDto open = reportingService.countOpenReports();

        // Which verdict is which severity is the dashboard's call, not the
        // reporting module's. `clean` is deliberately unmapped: an untriaged
        // report the AI cleared is not an alert waiting for anyone.
        int critical = Math.toIntExact(open.threat());
        int warning = Math.toIntExact(open.spam());
        return new OpenAlertCounts(critical + warning, critical, warning);
    }
}
