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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Runs the analytics half of an AIDA orchestration: recompute every in-scope
 * user's risk score, and for each user now at or above {@link #AT_RISK_THRESHOLD}
 * request remediation enrollment ({@code RemediationEnrollmentRequestedEvent},
 * handled by the learning module). When done it emits
 * {@code AidaOrchestrationCompletedEvent} so the AI module finalises the run.
 * <p>
 * {@link ApplicationModuleListener} runs asynchronously in its own transaction;
 * the tenant is set from the event so {@link AnalyticsService#recomputeRisk} and
 * the RLS-scoped writes resolve the right tenant.
 */
@Component
class AidaOrchestrationListener {

    private static final Logger LOG = LoggerFactory.getLogger(AidaOrchestrationListener.class);

    /** Recomputed score (0..100) at or above which a user is auto-enrolled in remediation. */
    static final int AT_RISK_THRESHOLD = 60;

    private final AnalyticsService analyticsService;
    private final RiskScoreRepository riskScoreRepository;
    private final EventPublisher eventPublisher;

    AidaOrchestrationListener(AnalyticsService analyticsService,
                              RiskScoreRepository riskScoreRepository,
                              EventPublisher eventPublisher) {
        this.analyticsService = analyticsService;
        this.riskScoreRepository = riskScoreRepository;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    void on(AidaOrchestrationRequestedEvent event) {
        UUID tenantId = event.tenantId();
        TenantContext.set(tenantId.toString());
        int evaluated = 0;
        int enrolled = 0;
        try {
            for (UUID userId : targetUsers(tenantId, event.scope(), event.scopeId())) {
                RiskScore score = analyticsService.recomputeRisk(userId);
                evaluated++;
                if (score.getValue() >= AT_RISK_THRESHOLD) {
                    eventPublisher.publish(
                            new RemediationEnrollmentRequestedEvent(tenantId, userId, event.runId()));
                    enrolled++;
                }
            }
        } finally {
            TenantContext.clear();
        }
        eventPublisher.publish(
                new AidaOrchestrationCompletedEvent(tenantId, event.runId(), evaluated, enrolled, event.locale()));
        LOG.info("AIDA analytics run={} scope={} evaluated={} atRisk={}",
                event.runId(), event.scope(), evaluated, enrolled);
    }

    /**
     * The users a run targets: a single user for scope {@code "user"} with an id,
     * otherwise every user in the tenant that has a risk score (org/department
     * scopes recompute the whole tenant).
     */
    private List<UUID> targetUsers(UUID tenantId, String scope, UUID scopeId) {
        if ("user".equalsIgnoreCase(scope) && scopeId != null) {
            return List.of(scopeId);
        }
        return riskScoreRepository.findDistinctScopeIds(tenantId, RiskScope.USER);
    }
}
