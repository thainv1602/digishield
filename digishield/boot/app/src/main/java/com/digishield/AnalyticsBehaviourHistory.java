package com.digishield;

import com.digishield.analytics.api.AnalyticsService;
import com.digishield.learning.api.BehaviourHistory;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wires the learning module's {@link BehaviourHistory} SPI to analytics, which
 * already records every simulation click as a risk signal — so remediation can
 * escalate on a repeat rather than repeating itself.
 */
@Component
class AnalyticsBehaviourHistory implements BehaviourHistory {

    private final AnalyticsService analyticsService;

    AnalyticsBehaviourHistory(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public int simulationClicks(UUID tenantId, UUID userId) {
        return analyticsService.simulationClicks(tenantId, userId);
    }

    @Override
    public int confirmedReports(UUID tenantId, UUID userId) {
        return analyticsService.confirmedReports(tenantId, userId);
    }
}
