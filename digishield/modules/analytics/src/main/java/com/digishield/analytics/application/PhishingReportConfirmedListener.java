package com.digishield.analytics.application;

import com.digishield.analytics.api.AnalyticsService;
import com.digishield.contracts.events.PhishingReportConfirmedEvent;
import com.digishield.shared.tenantcontext.TenantContext;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens for {@code PhishingReportConfirmedEvent} from the reporting module and
 * records it as a vigilant (risk-lowering) signal for the reporter, recomputing
 * their risk score. Reporting a real threat is good behaviour, so it reduces the
 * user's phish-prone score.
 * <p>
 * {@link ApplicationModuleListener} runs asynchronously in its own transaction,
 * so the tenant is passed explicitly to the service. {@link TenantContext} is
 * also set for the duration of the call so the row-level-security aspect can
 * scope the writes to this tenant.
 */
@Component
class PhishingReportConfirmedListener {

    private final AnalyticsService analyticsService;

    PhishingReportConfirmedListener(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @ApplicationModuleListener
    void on(PhishingReportConfirmedEvent event) {
        String previousTenant = TenantContext.get();
        TenantContext.set(event.tenantId().toString());
        try {
            analyticsService.recordConfirmedReport(event.tenantId(), event.userId());
        } finally {
            TenantContext.restore(previousTenant);
        }
    }
}
