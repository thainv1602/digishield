package com.digishield.analytics.application;

import com.digishield.analytics.api.AnalyticsService;
import com.digishield.contracts.events.UserClickedSimulationEvent;
import com.digishield.shared.tenantcontext.TenantContext;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Records a phishing-simulation click as a risk signal and recomputes the
 * user's risk score, closing the adaptive-learning loop: a risky action raises
 * the score, which downstream consumers ({@code RiskRecomputedEvent}) act on.
 * <p>
 * {@link ApplicationModuleListener} runs asynchronously in its own transaction,
 * so the tenant is passed explicitly to the service. {@link TenantContext} is
 * also set for the duration of the call so the row-level-security aspect can
 * scope the writes to this tenant.
 */
@Component
class SimulationClickRiskListener {

    private final AnalyticsService analyticsService;

    SimulationClickRiskListener(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @ApplicationModuleListener
    void on(UserClickedSimulationEvent event) {
        String previousTenant = TenantContext.get();
        TenantContext.set(event.tenantId().toString());
        try {
            analyticsService.recordSimulationClick(event.tenantId(), event.userId());
        } finally {
            TenantContext.restore(previousTenant);
        }
    }
}
