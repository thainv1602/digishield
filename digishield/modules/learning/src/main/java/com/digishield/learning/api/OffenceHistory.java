package com.digishield.learning.api;

import java.util.UUID;

/**
 * SPI reporting how many simulations a person has already clicked.
 * <p>
 * Remediation used to be the same course every time, so a fifth click was
 * answered exactly like a first. Learning does not own the click record;
 * analytics does, and the boot application bridges the two.
 */
public interface OffenceHistory {

    /** Clicks by this user inside analytics' scoring window. */
    int simulationClicks(UUID tenantId, UUID userId);
}
