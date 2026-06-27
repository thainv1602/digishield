package com.digishield.analytics.application;

import com.digishield.analytics.domain.RiskScope;
import com.digishield.analytics.domain.RiskScore;
import com.digishield.analytics.infrastructure.DepartmentRiskRepository;
import com.digishield.analytics.infrastructure.RiskScoreRepository;
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
    private EventPublisher eventPublisher;

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
        assertThat(result).isSameAs(persisted);

        // Assert: published event mirrors the persisted score
        verify(eventPublisher).publish(eventCaptor.capture());
        RiskRecomputedEvent event = eventCaptor.getValue();
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.score()).isEqualTo(persisted.getValue());
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
}
