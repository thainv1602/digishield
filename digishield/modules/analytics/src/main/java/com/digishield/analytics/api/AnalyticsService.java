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
     * Recomputes the risk score for a user and emits
     * {@code RiskRecomputedEvent}.
     *
     * @param userId the user to recompute
     * @return the new risk score record
     */
    RiskScore recomputeRisk(UUID userId);

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
