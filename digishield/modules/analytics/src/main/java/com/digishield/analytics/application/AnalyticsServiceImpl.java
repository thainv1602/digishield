package com.digishield.analytics.application;

import com.digishield.analytics.api.AnalyticsService;
import com.digishield.analytics.api.dto.BenchmarkDto;
import com.digishield.analytics.api.dto.DashboardDto;
import com.digishield.analytics.api.dto.RiskScoreDto;
import com.digishield.analytics.domain.DepartmentRisk;
import com.digishield.analytics.domain.RiskScope;
import com.digishield.analytics.domain.RiskScore;
import com.digishield.analytics.infrastructure.DepartmentRiskRepository;
import com.digishield.analytics.infrastructure.RiskScoreRepository;
import com.digishield.contracts.events.RiskRecomputedEvent;
import com.digishield.shared.messaging.EventPublisher;
import com.digishield.shared.tenantcontext.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link AnalyticsService}.
 */
@Service
@Transactional
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final double INDUSTRY_AVG_PHISH_PRONE_PCT = 11.2;

    private final RiskScoreRepository riskScoreRepository;
    private final DepartmentRiskRepository departmentRiskRepository;
    private final EventPublisher eventPublisher;

    public AnalyticsServiceImpl(RiskScoreRepository riskScoreRepository,
                                DepartmentRiskRepository departmentRiskRepository,
                                EventPublisher eventPublisher) {
        this.riskScoreRepository = riskScoreRepository;
        this.departmentRiskRepository = departmentRiskRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public RiskScore recomputeRisk(UUID userId) {
        UUID tenantId = TenantContext.requireUuid();

        // Minimal body: the actual scoring logic would go here.
        int value = computeScore(tenantId, userId);

        RiskScore score = new RiskScore(
                UUID.randomUUID(), tenantId, RiskScope.USER, userId, value, Instant.now());
        RiskScore saved = riskScoreRepository.save(score);

        eventPublisher.publish(new RiskRecomputedEvent(tenantId, userId, value));
        return saved;
    }

    @Override
    public int benchmark(RiskScope scope) {
        UUID tenantId = TenantContext.requireUuid();
        List<RiskScore> scores = riskScoreRepository.findByTenantIdAndScope(tenantId, scope);
        if (scores.isEmpty()) {
            return 0;
        }
        long sum = scores.stream().mapToInt(RiskScore::getValue).sum();
        return (int) (sum / scores.size());
    }

    @Override
    @Transactional(readOnly = true)
    public RiskScoreDto riskFor(RiskScope scope, UUID scopeId) {
        UUID tenantId = TenantContext.requireUuid();

        List<RiskScore> scores = scopeId != null
                ? riskScoreRepository.findByTenantIdAndScopeAndScopeId(tenantId, scope, scopeId)
                : riskScoreRepository.findByTenantIdAndScope(tenantId, scope);

        RiskScore latest = scores.stream()
                .max(Comparator.comparing(RiskScore::getComputedAt))
                .orElse(null);

        if (latest == null) {
            return new RiskScoreDto(scope.name().toLowerCase(), scopeId, 0, Instant.now());
        }
        return new RiskScoreDto(
                latest.getScope().name().toLowerCase(),
                latest.getScopeId(),
                latest.getValue(),
                latest.getComputedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public BenchmarkDto benchmarkRates() {
        UUID tenantId = TenantContext.requireUuid();
        double orgPct = orgPhishPronePct(tenantId);
        return new BenchmarkDto(orgPct, INDUSTRY_AVG_PHISH_PRONE_PCT);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        UUID tenantId = TenantContext.requireUuid();

        int orgRisk = latestOrgRisk(tenantId);
        double orgPhishProne = orgPhishPronePct(tenantId);

        List<DepartmentRisk> depts = departmentRiskRepository.findByTenantIdOrderByRiskScoreDesc(tenantId);
        List<DashboardDto.Department> deptViews = depts.stream()
                .map(d -> new DashboardDto.Department(d.getName(), d.getRiskScore()))
                .toList();

        List<DashboardDto.Benchmark> benchmarks = List.of(
                new DashboardDto.Benchmark("Cơ quan ABC (bạn)", orgPhishProne, true),
                new DashboardDto.Benchmark("TB cơ quan nhà nước", INDUSTRY_AVG_PHISH_PRONE_PCT, false),
                new DashboardDto.Benchmark("TB ngành tài chính", 14.8, false));

        List<DashboardDto.TrendPoint> trend = List.of(
                new DashboardDto.TrendPoint("2026-04-01", 59),
                new DashboardDto.TrendPoint("2026-04-15", 58),
                new DashboardDto.TrendPoint("2026-05-01", 60),
                new DashboardDto.TrendPoint("2026-05-15", 61),
                new DashboardDto.TrendPoint("2026-06-01", 59),
                new DashboardDto.TrendPoint("2026-06-10", 62),
                new DashboardDto.TrendPoint("2026-06-18", 63),
                new DashboardDto.TrendPoint("2026-06-24", 64),
                new DashboardDto.TrendPoint("2026-06-26", 63),
                new DashboardDto.TrendPoint("2026-06-27", orgRisk));

        List<DashboardDto.RecentReport> recent = List.of(
                new DashboardDto.RecentReport("r1", "\"Khóa tài khoản — xác minh ngay\"", "Nguyen A", "2p", "threat"),
                new DashboardDto.RecentReport("r2", "\"Trúng thưởng iPhone 15\"", "Tran B", "8p", "spam"),
                new DashboardDto.RecentReport("r3", "\"Lịch họp tuần 27 – phòng A3\"", "Le C", "15p", "clean"));

        return new DashboardDto(
                orgRisk,
                3,
                orgPhishProne,
                2.1,
                INDUSTRY_AVG_PHISH_PRONE_PCT,
                91,
                new DashboardDto.OpenAlerts(3, 1, 2),
                trend,
                benchmarks,
                deptViews,
                recent);
    }

    private int latestOrgRisk(UUID tenantId) {
        return riskScoreRepository.findByTenantIdAndScope(tenantId, RiskScope.ORG).stream()
                .max(Comparator.comparing(RiskScore::getComputedAt))
                .map(RiskScore::getValue)
                .orElse(0);
    }

    /**
     * Demo phish-prone rate derived from the org risk score; falls back to the
     * seeded reference value when no org score is present.
     */
    private double orgPhishPronePct(UUID tenantId) {
        int orgRisk = latestOrgRisk(tenantId);
        if (orgRisk <= 0) {
            return 8.4;
        }
        // Smooth mapping so the demo number tracks risk but stays realistic.
        return Math.round(orgRisk * 0.135 * 10.0) / 10.0;
    }

    /**
     * Placeholder for risk score computation. Returns a minimal value.
     */
    private int computeScore(UUID tenantId, UUID userId) {
        return 0;
    }
}
