package com.digishield.simulation.api;

import java.util.Map;
import java.util.UUID;

/**
 * SPI reporting how far each person has got with the remediation training a
 * simulation click assigns them.
 * <p>
 * Clicking publishes {@code UserClickedSimulationEvent}, which the learning
 * module turns into an enrolment. The results table reports the other end of
 * that flow, so it has to ask learning rather than guess.
 */
public interface RemediationStatusProvider {

    /**
     * Remediation state per user for the current tenant. Users absent from the
     * map have none.
     */
    Map<UUID, Remediation> statusByUser();

    /** How far a person has got, from the results table's point of view. */
    enum Remediation {
        /** Assigned and under way, including overdue. */
        IN_PROGRESS,
        /** Finished. */
        COMPLETED
    }
}
