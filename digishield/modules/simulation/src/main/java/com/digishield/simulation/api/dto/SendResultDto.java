package com.digishield.simulation.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of launching (sending) a simulation campaign. Because outbound email
 * isn't wired to a real MTA here, the per-recipient tracking links are returned
 * so the campaign can be exercised end to end (following a link records a CLICK).
 *
 * @param campaignId     the campaign that was launched
 * @param status         the campaign status after launch (lowercase)
 * @param recipientCount how many recipients were delivered to
 * @param recipients     per-recipient tracking links
 */
public record SendResultDto(
        UUID campaignId,
        String status,
        int recipientCount,
        List<Recipient> recipients) {

    /**
     * A single recipient's tracking link.
     *
     * @param userId   the targeted user
     * @param token    the opaque tracking token
     * @param trackUrl the absolute simulation link (records a CLICK when followed)
     */
    public record Recipient(UUID userId, UUID token, String trackUrl) {
    }
}
