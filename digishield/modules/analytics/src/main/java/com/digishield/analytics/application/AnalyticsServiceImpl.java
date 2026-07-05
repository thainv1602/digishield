package com.digishield.analytics.application;

import com.digishield.analytics.api.AnalyticsService;
import com.digishield.analytics.api.dto.BenchmarkDto;
import com.digishield.analytics.api.dto.DashboardDto;
import com.digishield.analytics.api.dto.RiskScoreDto;
import com.digishield.analytics.domain.DepartmentRisk;
import com.digishield.analytics.domain.RiskScope;
import com.digishield.analytics.domain.RiskScore;
import com.digishield.analytics.domain.RiskSignal;
import com.digishield.analytics.domain.RiskSignalType;
import com.digishield.analytics.infrastructure.DepartmentRiskRepository;
import com.digishield.analytics.infrastructure.RiskScoreRepository;
import com.digishield.analytics.infrastructure.RiskSignalRepository;
import com.digishield.contracts.events.RiskRecomputedEvent;
import com.digishield.shared.messaging.EventPublisher;
import com.digishield.shared.tenantcontext.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

    /** Baseline risk for a user with no recent negative signals. */
    private static final int BASE_RISK = 5;
    /** Lower bound for a risk score. */
    private static final int MIN_RISK = 0;
    /** Upper bound for a risk score. */
    private static final int MAX_RISK = 100;
    /** Only signals from the last {@code SCORING_WINDOW} count toward the score. */
    private static final Duration SCORING_WINDOW = Duration.ofDays(90);

    private final RiskScoreRepository riskScoreRepository;
    private final DepartmentRiskRepository departmentRiskRepository;
    private final RiskSignalRepository riskSignalRepository;
    private final EventPublisher eventPublisher;

    public AnalyticsServiceImpl(RiskScoreRepository riskScoreRepository,
                                DepartmentRiskRepository departmentRiskRepository,
                                RiskSignalRepository riskSignalRepository,
                                EventPublisher eventPublisher) {
        this.riskScoreRepository = riskScoreRepository;
        this.departmentRiskRepository = departmentRiskRepository;
        this.riskSignalRepository = riskSignalRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public RiskScore recomputeRisk(UUID userId) {
        return doRecompute(TenantContext.requireUuid(), userId);
    }

    @Override
    public RiskScore recordSimulationClick(UUID tenantId, UUID userId) {
        return recordSignal(tenantId, userId, RiskSignalType.SIMULATION_CLICK);
    }

    @Override
    public RiskScore recordConfirmedReport(UUID tenantId, UUID userId) {
        return recordSignal(tenantId, userId, RiskSignalType.PHISHING_REPORT_CONFIRMED);
    }

    /**
     * Persists a behavioural signal of the given type, then recomputes the
     * user's risk score from their recent signals.
     */
    private RiskScore recordSignal(UUID tenantId, UUID userId, RiskSignalType type) {
        riskSignalRepository.save(new RiskSignal(
                UUID.randomUUID(), tenantId, userId, type, type.getDefaultWeight(), Instant.now()));
        return doRecompute(tenantId, userId);
    }

    /**
     * Computes, persists and announces a user's risk score for the given tenant.
     */
    private RiskScore doRecompute(UUID tenantId, UUID userId) {
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

        List<DashboardDto.TrendPoint> trend = orgRiskTrend(tenantId);

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
     * Org-risk trend as a time series derived from the persisted org-scope risk
     * scores (oldest → newest), each point dated by its {@code computedAt} day.
     * Empty when the tenant has no org-scope history yet.
     */
    private List<DashboardDto.TrendPoint> orgRiskTrend(UUID tenantId) {
        return riskScoreRepository.findByTenantIdAndScope(tenantId, RiskScope.ORG).stream()
                .sorted(Comparator.comparing(RiskScore::getComputedAt))
                .map(s -> new DashboardDto.TrendPoint(
                        s.getComputedAt().atZone(ZoneOffset.UTC).toLocalDate().toString(),
                        s.getValue()))
                .toList();
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
     * Risk score for a user: a baseline plus the summed weight of their recent
     * behavioural signals, clamped to {@link #MIN_RISK}..{@link #MAX_RISK}.
     * Risky actions (e.g. simulation clicks) carry positive weight; vigilant
     * ones (e.g. confirmed phishing reports) carry negative weight. Higher means
     * more phish-prone.
     */
    private int computeScore(UUID tenantId, UUID userId) {
        Instant since = Instant.now().minus(SCORING_WINDOW);
        int signalWeight = riskSignalRepository
                .findByTenantIdAndUserIdAndOccurredAtAfter(tenantId, userId, since)
                .stream()
                .mapToInt(RiskSignal::getWeight)
                .sum();
        return Math.max(MIN_RISK, Math.min(MAX_RISK, BASE_RISK + signalWeight));
    }
}
