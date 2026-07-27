package com.digishield.learning.api;

import java.util.Optional;
import java.util.UUID;

/**
 * SPI for naming a learner when their score is first recorded.
 * <p>
 * A leaderboard has to show a person, not an id, and learning does not own the
 * directory. Bridged in the boot application to the auth module, as the
 * simulation and analytics modules do for the same reason.
 */
public interface LearnerDirectory {

    /** The learner, if the current tenant has them. */
    Optional<Learner> find(UUID userId);

    /**
     * @param displayName name to show, or {@code null} when unset
     * @param department  department name, or {@code null} when unassigned
     */
    record Learner(String displayName, String department) {
    }
}
