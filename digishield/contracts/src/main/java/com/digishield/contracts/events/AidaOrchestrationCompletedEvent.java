package com.digishield.contracts.events;

import java.util.UUID;

/**
 * Emitted by the analytics module when an AIDA orchestration run finishes. The
 * AI module handles it by finalising the matching {@code AidaRun} record (status
 * and summary) so the admin console reflects the real outcome.
 *
 * @param tenantId       the tenant the run belongs to
 * @param runId          id of the {@code AidaRun} record to finalise
 * @param usersEvaluated number of users whose risk was recomputed
 * @param usersEnrolled  number of at-risk users sent for remediation enrollment
 * @param locale         BCP 47 language tag of the request that triggered the run, propagated
 *                       from {@code AidaOrchestrationRequestedEvent} so the run summary is
 *                       written in the same language the trigger used; may be {@code null} to
 *                       fall back to the default locale
 */
public record AidaOrchestrationCompletedEvent(UUID tenantId, UUID runId,
                                              int usersEvaluated, int usersEnrolled, String locale) {
}
