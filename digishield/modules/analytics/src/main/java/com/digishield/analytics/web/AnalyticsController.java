package com.digishield.analytics.web;

import com.digishield.analytics.api.AnalyticsService;
import com.digishield.analytics.api.dto.BenchmarkDto;
import com.digishield.analytics.api.dto.DashboardDto;
import com.digishield.analytics.api.dto.RiskScoreDto;
import com.digishield.analytics.domain.RiskScope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * REST controller for the analytics module.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('ANALYST','MANAGER')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Risk score by scope. Matches {@code GET /analytics/risk?scope=&scope_id=}.
     *
     * @param scope   one of "user", "dept", "org" (defaults to "org")
     * @param scopeId optional scope identifier
     */
    @GetMapping("/risk")
    public ResponseEntity<RiskScoreDto> risk(
            @RequestParam(value = "scope", defaultValue = "org") String scope,
            @RequestParam(value = "scope_id", required = false) UUID scopeId) {
        RiskScope parsed = RiskScope.valueOf(scope.trim().toUpperCase());
        return ResponseEntity.ok(analyticsService.riskFor(parsed, scopeId));
    }

    /**
     * Org vs. industry phish-prone benchmark. Matches {@code GET /analytics/benchmark}.
     */
    @GetMapping("/benchmark")
    public ResponseEntity<BenchmarkDto> benchmark() {
        return ResponseEntity.ok(analyticsService.benchmarkRates());
    }

    /**
     * Aggregated dashboard payload (KPIs + 90-day trend + departments + recent
     * reports) for the admin dashboard. Convenience endpoint so the FE needs a
     * single call. Not part of the OpenAPI core but additive.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDto> dashboard() {
        return ResponseEntity.ok(analyticsService.dashboard());
    }
}
