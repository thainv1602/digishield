package com.digishield.analytics.application;

import com.digishield.analytics.api.RecentReportsProvider;
import com.digishield.analytics.api.dto.DashboardDto;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnalyticsServiceImpl}.
 * <p>
 * Pure Mockito unit tests: no Spring context, no real database.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private RiskScoreRepository riskScoreRepository;

    @Mock
    private DepartmentRiskRepository departmentRiskRepository;

    @Mock
    private RiskSignalRepository riskSignalRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private com.digishield.shared.tenantcontext.Messages messages;

    @Mock
    private RecentReportsProvider recentReportsProvider;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    @Captor
    private ArgumentCaptor<RiskScore> scoreCaptor;

    @Captor
    private ArgumentCaptor<RiskRecomputedEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void recomputeRisk_persistsUserScopedScoreAndPublishesEvent() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(riskSignalRepository
                .findByTenantIdAndUserIdAndOccurredAtAfter(eq(TENANT_ID), eq(userId), any(Instant.class)))
                .thenReturn(List.of());
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RiskScore result = analyticsService.recomputeRisk(userId);

        // Assert: persisted entity
        verify(riskScoreRepository).save(scoreCaptor.capture());
        RiskScore persisted = scoreCaptor.getValue();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(persisted.getScope()).isEqualTo(RiskScope.USER);
        assertThat(persisted.getScopeId()).isEqualTo(userId);
        assertThat(persisted.getComputedAt()).isNotNull();
        // No signals → baseline risk.
        assertThat(persisted.getValue()).isEqualTo(5);
        assertThat(result).isSameAs(persisted);

        // Assert: published event mirrors the persisted score
        verify(eventPublisher).publish(eventCaptor.capture());
        RiskRecomputedEvent event = eventCaptor.getValue();
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.score()).isEqualTo(persisted.getValue());
    }

    @Test
    void computeScore_sumsRecentSignalWeightsOverBaseline() {
        // Arrange: two simulation clicks (weight 25 each) → 5 + 50 = 55
        UUID userId = UUID.randomUUID();
        when(riskSignalRepository
                .findByTenantIdAndUserIdAndOccurredAtAfter(eq(TENANT_ID), eq(userId), any(Instant.class)))
                .thenReturn(List.of(signal(userId), signal(userId)));
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RiskScore result = analyticsService.recomputeRisk(userId);

        // Assert
        assertThat(result.getValue()).isEqualTo(55);
    }

    @Test
    void computeScore_isCappedAt100() {
        // Arrange: five clicks → 5 + 125 would exceed the cap
        UUID userId = UUID.randomUUID();
        List<RiskSignal> many = List.of(
                signal(userId), signal(userId), signal(userId), signal(userId), signal(userId));
        when(riskSignalRepository
                .findByTenantIdAndUserIdAndOccurredAtAfter(eq(TENANT_ID), eq(userId), any(Instant.class)))
                .thenReturn(many);
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RiskScore result = analyticsService.recomputeRisk(userId);

        // Assert
        assertThat(result.getValue()).isEqualTo(100);
    }

    @Test
    void recordSimulationClick_savesSignalThenRecomputesUsingExplicitTenant() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(riskSignalRepository
                .findByTenantIdAndUserIdAndOccurredAtAfter(eq(TENANT_ID), eq(userId), any(Instant.class)))
                .thenReturn(List.of(signal(userId)));
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantContext.clear(); // tenant comes from the argument, not the context

        // Act
        RiskScore result = analyticsService.recordSimulationClick(TENANT_ID, userId);

        // Assert: a SIMULATION_CLICK signal was recorded for this tenant/user
        ArgumentCaptor<RiskSignal> signalCaptor = ArgumentCaptor.forClass(RiskSignal.class);
        verify(riskSignalRepository).save(signalCaptor.capture());
        RiskSignal saved = signalCaptor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo(RiskSignalType.SIMULATION_CLICK);
        assertThat(saved.getWeight()).isEqualTo(25);

        // Assert: score recomputed (baseline + the one recorded click)
        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getValue()).isEqualTo(30);
    }

    @Test
    void recordConfirmedReport_recordsVigilantSignalAndFloorsScoreAtZero() {
        // Arrange: a single confirmed-report signal (weight -15) → 5 - 15 floored to 0
        UUID userId = UUID.randomUUID();
        RiskSignal reportSignal = new RiskSignal(
                UUID.randomUUID(), TENANT_ID, userId, RiskSignalType.PHISHING_REPORT_CONFIRMED, -15, Instant.now());
        when(riskSignalRepository
                .findByTenantIdAndUserIdAndOccurredAtAfter(eq(TENANT_ID), eq(userId), any(Instant.class)))
                .thenReturn(List.of(reportSignal));
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantContext.clear(); // tenant comes from the argument, not the context

        // Act
        RiskScore result = analyticsService.recordConfirmedReport(TENANT_ID, userId);

        // Assert: a PHISHING_REPORT_CONFIRMED signal was recorded with negative weight
        ArgumentCaptor<RiskSignal> signalCaptor = ArgumentCaptor.forClass(RiskSignal.class);
        verify(riskSignalRepository).save(signalCaptor.capture());
        RiskSignal saved = signalCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(RiskSignalType.PHISHING_REPORT_CONFIRMED);
        assertThat(saved.getWeight()).isEqualTo(-15);

        // Assert: score never goes below zero
        assertThat(result.getValue()).isZero();
    }

    private RiskSignal signal(UUID userId) {
        return new RiskSignal(
                UUID.randomUUID(), TENANT_ID, userId, RiskSignalType.SIMULATION_CLICK, 25, Instant.now());
    }

    @Test
    void benchmark_whenScoresExist_returnsIntegerAverage() {
        // Arrange
        List<RiskScore> scores = List.of(
                score(40),
                score(50),
                score(75)); // average = 165 / 3 = 55
        when(riskScoreRepository.findByTenantIdAndScope(TENANT_ID, RiskScope.ORG)).thenReturn(scores);

        // Act
        int result = analyticsService.benchmark(RiskScope.ORG);

        // Assert
        assertThat(result).isEqualTo(55);
    }

    @Test
    void benchmark_whenNoScores_returnsZero() {
        // Arrange
        when(riskScoreRepository.findByTenantIdAndScope(TENANT_ID, RiskScope.DEPT)).thenReturn(List.of());

        // Act
        int result = analyticsService.benchmark(RiskScope.DEPT);

        // Assert
        assertThat(result).isZero();
    }

    private RiskScore score(int value) {
        return new RiskScore(
                UUID.randomUUID(), TENANT_ID, RiskScope.ORG, UUID.randomUUID(), value, Instant.now());
    }

    @Test
    void dashboard_buildsTrendFromOrgRiskHistoryInChronologicalOrder() {
        // Arrange: org-scope history in deliberately unordered input
        List<RiskScore> orgScores = List.of(
                orgScore(61, Instant.parse("2026-06-01T00:00:00Z")),
                orgScore(59, Instant.parse("2026-04-01T00:00:00Z")),
                orgScore(60, Instant.parse("2026-05-01T00:00:00Z")));
        when(riskScoreRepository.findByTenantIdAndScope(TENANT_ID, RiskScope.ORG)).thenReturn(orgScores);
        when(departmentRiskRepository.findByTenantIdOrderByRiskScoreDesc(TENANT_ID)).thenReturn(List.of());

        // Act
        DashboardDto dto = analyticsService.dashboard();

        // Assert: trend derived from data, oldest → newest (not hardcoded)
        assertThat(dto.riskTrend()).extracting(DashboardDto.TrendPoint::date)
                .containsExactly("2026-04-01", "2026-05-01", "2026-06-01");
        assertThat(dto.riskTrend()).extracting(DashboardDto.TrendPoint::value)
                .containsExactly(59, 60, 61);
    }

    private RiskScore orgScore(int value, Instant at) {
        return new RiskScore(UUID.randomUUID(), TENANT_ID, RiskScope.ORG, TENANT_ID, value, at);
    }

    @Test
    void dashboard_recentReports_comeFromProviderMappedOntoDto() {
        // Arrange: no risk/dept history; the recent-reports provider returns one report
        when(riskScoreRepository.findByTenantIdAndScope(TENANT_ID, RiskScope.ORG)).thenReturn(List.of());
        when(departmentRiskRepository.findByTenantIdOrderByRiskScoreDesc(TENANT_ID)).thenReturn(List.of());
        when(recentReportsProvider.recentReports(anyInt())).thenReturn(List.of(
                new RecentReportsProvider.RecentReportView("id-1", "Khoa tai khoan", "Nguyen A", "2p", "threat")));

        // Act
        DashboardDto dto = analyticsService.dashboard();

        // Assert: sourced from the provider (not hardcoded demo rows) and mapped field-for-field
        assertThat(dto.recentReports()).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo("id-1");
            assertThat(r.title()).isEqualTo("Khoa tai khoan");
            assertThat(r.who()).isEqualTo("Nguyen A");
            assertThat(r.age()).isEqualTo("2p");
            assertThat(r.aiLabel()).isEqualTo("threat");
        });
    }
}
