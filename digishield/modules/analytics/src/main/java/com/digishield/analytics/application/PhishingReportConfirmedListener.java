package com.digishield.analytics.application;

import com.digishield.contracts.events.PhishingReportConfirmedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens for {@code PhishingReportConfirmedEvent} from the reporting module
 * and flags that the related risk score needs to be recomputed.
 * <p>
 * Minimal body: in practice this would enqueue a risk recomputation job or
 * call {@code AnalyticsService.recomputeRisk(...)}.
 */
@Component
public class PhishingReportConfirmedListener {

    @ApplicationModuleListener
    public void on(PhishingReportConfirmedEvent event) {
        // TODO: flag that risk needs recomputing for the corresponding tenant/report.
        // tenantId = event.tenantId(), reportId = event.reportId()
    }
}
