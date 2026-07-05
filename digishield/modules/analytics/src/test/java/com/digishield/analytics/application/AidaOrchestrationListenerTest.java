package com.digishield.analytics.application;

import com.digishield.analytics.api.AnalyticsService;
import com.digishield.analytics.domain.RiskScope;
import com.digishield.analytics.domain.RiskScore;
import com.digishield.analytics.infrastructure.RiskScoreRepository;
import com.digishield.contracts.events.AidaOrchestrationCompletedEvent;
import com.digishield.contracts.events.AidaOrchestrationRequestedEvent;
import com.digishield.contracts.events.RemediationEnrollmentRequestedEvent;
import com.digishield.shared.messaging.EventPublisher;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AidaOrchestrationListener}.
 */
class AidaOrchestrationListenerTest {

    private final AnalyticsService analyticsService = mock(AnalyticsService.class);
    private final RiskScoreRepository riskScoreRepository = mock(RiskScoreRepository.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private final AidaOrchestrationListener listener =
            new AidaOrchestrationListener(analyticsService, riskScoreRepository, eventPublisher);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private RiskScore scoreOf(UUID tenantId, UUID userId, int value) {
        return new RiskScore(UUID.randomUUID(), tenantId, RiskScope.USER, userId, value, Instant.now());
    }

    @Test
    void on_orgScope_recomputesEveryUserAndEnrollsOnlyAtRisk() {
        // Arrange: two users in the tenant — one at-risk, one not.
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID atRisk = UUID.randomUUID();
        UUID safe = UUID.randomUUID();
        when(riskScoreRepository.findDistinctScopeIds(tenantId, RiskScope.USER))
                .thenReturn(List.of(atRisk, safe));
        when(analyticsService.recomputeRisk(atRisk))
                .thenReturn(scoreOf(tenantId, atRisk, AidaOrchestrationListener.AT_RISK_THRESHOLD));
        when(analyticsService.recomputeRisk(safe))
                .thenReturn(scoreOf(tenantId, safe, AidaOrchestrationListener.AT_RISK_THRESHOLD - 1));

        // Act
        listener.on(new AidaOrchestrationRequestedEvent(tenantId, runId, "org", null));

        // Assert: only the at-risk user is sent for remediation...
        verify(eventPublisher).publish(new RemediationEnrollmentRequestedEvent(tenantId, atRisk, runId));
        verify(eventPublisher, never()).publish(new RemediationEnrollmentRequestedEvent(tenantId, safe, runId));
        // ...and the run completes with real evaluated/enrolled counts.
        verify(eventPublisher).publish(new AidaOrchestrationCompletedEvent(tenantId, runId, 2, 1));
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void on_userScope_recomputesOnlyThatUser() {
        // Arrange
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(analyticsService.recomputeRisk(userId)).thenReturn(scoreOf(tenantId, userId, 10));

        // Act
        listener.on(new AidaOrchestrationRequestedEvent(tenantId, runId, "user", userId));

        // Assert: no tenant-wide lookup, single evaluation, nobody enrolled.
        verify(riskScoreRepository, never()).findDistinctScopeIds(tenantId, RiskScope.USER);
        verify(analyticsService).recomputeRisk(userId);
        verify(eventPublisher).publish(new AidaOrchestrationCompletedEvent(tenantId, runId, 1, 0));
    }
}
