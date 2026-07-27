package com.digishield.simulation.api;

import java.util.List;
import java.util.UUID;

/**
 * SPI for naming the people a campaign was sent to.
 * <p>
 * A campaign stores recipients by id; the results table has to show a person.
 * Simulation does not own the directory, so the boot application bridges this to
 * the auth module — the same arrangement as {@code CampaignTemplateProvider}.
 */
public interface ParticipantDirectory {

    /** Everyone in the current tenant. Never {@code null}. */
    List<Participant> participants();

    /**
     * One person, reduced to what a results row shows.
     *
     * @param userId     the user
     * @param name       display name, or {@code null} when unset
     * @param department department name, or {@code null} when unassigned
     */
    record Participant(UUID userId, String name, String department) {
    }
}
