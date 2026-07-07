package com.digishield.contracts.events;

import java.util.UUID;

/**
 * Emitted by the AI module when an AIDA orchestration run is triggered. The
 * analytics module handles it: it recomputes risk for every user in scope and,
 * for those now at or above the at-risk threshold, requests remediation
 * enrollment (see {@code RemediationEnrollmentRequestedEvent}).
 *
 * @param tenantId the tenant the run belongs to
 * @param runId    id of the {@code AidaRun} record to finalise when the run completes
 * @param scope    target scope, e.g. {@code "org"}, {@code "user"} or a department key
 * @param scopeId  optional id the scope refers to (e.g. a user id for scope {@code "user"})
 * @param locale   BCP 47 language tag of the request that triggered the run (e.g.
 *                 {@code "en"}, {@code "vi"}), carried so the asynchronous completion
 *                 summary can be localized in the same language as the trigger; may be
 *                 {@code null} to fall back to the default locale
 */
public record AidaOrchestrationRequestedEvent(UUID tenantId, UUID runId, String scope, UUID scopeId,
                                              String locale) {
}
