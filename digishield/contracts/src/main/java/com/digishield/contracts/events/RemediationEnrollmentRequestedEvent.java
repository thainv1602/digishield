package com.digishield.contracts.events;

import java.util.UUID;

/**
 * Emitted by the analytics module during an AIDA orchestration run for each user
 * whose recomputed risk is at or above the at-risk threshold. The learning
 * module handles it by auto-enrolling the user into remediation training.
 * <p>
 * This is a deliberate, AIDA-scoped enrollment trigger — distinct from the
 * per-click {@code UserClickedSimulationEvent} path — so the two flows never
 * enroll off the same signal.
 *
 * @param tenantId the tenant the user belongs to
 * @param userId   the at-risk user to enroll
 * @param runId    id of the AIDA run that requested the enrollment
 */
public record RemediationEnrollmentRequestedEvent(UUID tenantId, UUID userId, UUID runId) {
}
