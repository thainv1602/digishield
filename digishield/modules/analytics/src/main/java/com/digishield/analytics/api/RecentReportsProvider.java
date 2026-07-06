package com.digishield.analytics.api;

import java.util.List;

/**
 * SPI for reading the current tenant's most recent phishing reports, so the
 * admin dashboard's "recent reports" panel is data-driven instead of demo data.
 * <p>
 * Analytics owns the shape it needs ({@link RecentReportView}); the boot
 * application bridges this to the reporting module (mirrors how the notification
 * module's {@code UserDirectory} is wired to auth), keeping analytics decoupled
 * from reporting.
 */
public interface RecentReportsProvider {

    /**
     * Returns up to {@code limit} of the tenant's most recent phishing reports,
     * newest first. Never {@code null}; empty when the tenant has none.
     *
     * @param limit maximum number of reports to return
     */
    List<RecentReportView> recentReports(int limit);

    /**
     * A recent phishing report as the dashboard needs it.
     *
     * @param id      report identifier (string form)
     * @param title   subject line of the reported message
     * @param who     reporting user's display name
     * @param age     relative age label (e.g. {@code "2p"})
     * @param aiLabel AI classification (clean|spam|threat)
     */
    record RecentReportView(String id, String title, String who, String age, String aiLabel) {
    }
}
