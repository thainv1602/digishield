package com.digishield.analytics.api;

import com.digishield.analytics.api.dto.BenchmarkDto;
import com.digishield.analytics.api.dto.DashboardDto;
import com.digishield.analytics.api.dto.RiskScoreDto;
import com.digishield.analytics.domain.RiskScope;
import com.digishield.analytics.domain.RiskScore;

import java.util.UUID;

/**
 * Public API of the analytics module.
 */
public interface AnalyticsService {

    /**
     * Cache region for {@link #dashboard()}. Declared here rather than in the
     * boot module's cache configuration because the annotation that fills it
     * lives in this module, and a cache name written twice is a cache that
     * silently stops being read the day the two spellings drift.
     */
    String DASHBOARD_CACHE = "analytics:dashboard";

    /**
     * Recomputes the risk score for a user and emits
     * {@code RiskRecomputedEvent}.
     *
     * @param userId the user to recompute
     * @return the new risk score record
     */
    RiskScore recomputeRisk(UUID userId);

    /**
     * Records a phishing-simulation click as a risk signal for the user and
     * recomputes their risk score. Called from the module listener for
     * {@code UserClickedSimulationEvent}, so the tenant is passed explicitly
     * rather than read from {@code TenantContext}.
     *
     * @param tenantId the tenant the user belongs to
     * @param userId   the user who clicked
     * @return the recomputed risk score record
     */
    RiskScore recordSimulationClick(UUID tenantId, UUID userId);

    /**
     * Records that the user reported an email later confirmed as a real threat
     * (a positive, risk-lowering signal) and recomputes their risk score.
     * Called from the module listener for {@code PhishingReportConfirmedEvent}.
     *
     * @param tenantId the tenant the user belongs to
     * @param userId   the user who reported the threat
     * @return the recomputed risk score record
     */
    RiskScore recordConfirmedReport(UUID tenantId, UUID userId);

    /**
     * How many simulations this user has clicked inside the scoring window.
     * <p>
     * Analytics already records each click as a risk signal; this counts them so
     * remediation can be pitched at someone's actual history rather than
     * treating a fifth click like a first.
     */
    int simulationClicks(UUID tenantId, UUID userId);

    /**
     * How many of this user's phishing reports triage confirmed as genuine,
     * inside the scoring window. Recorded already as a risk signal; counted here
     * so badges can be earned on evidence rather than on prose.
     */
    int confirmedReports(UUID tenantId, UUID userId);

    /**
     * Gets the average benchmark score for a scope.
     *
     * @param scope the scope to benchmark
     * @return the average score (0 if there is no data)
     */
    int benchmark(RiskScope scope);

    /**
     * Returns the latest persisted risk score for a scope.
     * <p>
     * Used by {@code GET /analytics/risk}. When {@code scopeId} is {@code null}
     * (org-wide), the most recent score for the scope is returned.
     *
     * @param scope   the scope ("user" | "dept" | "org")
     * @param scopeId the scope identifier, or {@code null} for an org-wide score
     * @return the risk score view
     */
    RiskScoreDto riskFor(RiskScope scope, UUID scopeId);

    /**
     * Returns the org vs. industry phish-prone benchmark.
     *
     * @return the benchmark view
     */
    BenchmarkDto benchmarkRates();

    /**
     * Returns the aggregated dashboard payload for the current tenant
     * (KPIs, trend, departments and recent reports) in a single call.
     *
     * @return the dashboard view
     */
    DashboardDto dashboard();
}
