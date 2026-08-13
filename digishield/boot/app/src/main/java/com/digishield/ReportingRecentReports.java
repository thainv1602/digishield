package com.digishield;

import com.digishield.analytics.api.RecentReportsProvider;
import com.digishield.reporting.api.ReportingService;
import com.digishield.reporting.api.dto.PhishingReportDto;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Wires the analytics module's {@link RecentReportsProvider} SPI to the reporting
 * module: reads the current tenant's newest phishing reports so the admin
 * dashboard's "recent reports" panel is data-driven. Lives in the boot app to
 * keep analytics decoupled from reporting (mirrors {@link AuthUserDirectory}).
 */
@Component
class ReportingRecentReports implements RecentReportsProvider {

    private final ReportingService reportingService;

    ReportingRecentReports(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @Override
    public List<RecentReportView> recentReports(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        // Limited in the query. This used to fetch every report the tenant had
        // and keep the first few, so the cost of the panel grew with history
        // rather than with the handful actually shown.
        return reportingService.listRecentReports(limit).stream()
                .map(ReportingRecentReports::toView)
                .toList();
    }

    private static RecentReportView toView(PhishingReportDto r) {
        return new RecentReportView(
                r.id() != null ? r.id().toString() : null,
                r.subject(),
                r.reporter(),
                r.ageLabel(),
                r.aiLabel());
    }
}
