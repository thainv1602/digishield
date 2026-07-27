package com.digishield.learning.api;

import java.util.UUID;

/**
 * SPI reporting what a person has actually done — the lapses and the catches.
 * <p>
 * Remediation reads the first to pitch a course at a repeat click; badges read
 * the second so they are earned on evidence rather than on a description
 * nothing could evaluate. Analytics records both as risk signals; learning owns
 * neither, so the boot application bridges them.
 */
public interface BehaviourHistory {

    /** Simulations this user clicked, inside analytics' scoring window. */
    int simulationClicks(UUID tenantId, UUID userId);

    /** Reports of theirs that triage confirmed, inside the same window. */
    int confirmedReports(UUID tenantId, UUID userId);
}
