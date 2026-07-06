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
        // listReports(null) returns all reports newest-first; take the head.
        return reportingService.listReports(null).stream()
                .limit(limit)
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
