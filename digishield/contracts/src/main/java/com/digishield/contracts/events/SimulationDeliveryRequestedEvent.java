package com.digishield.contracts.events;

import java.util.UUID;

/**
 * Emitted once per recipient when a simulation campaign is launched, asking the
 * notification module to actually deliver that recipient's tracking link over
 * the campaign's channel (email/SMS). Keeps the simulation module decoupled from
 * delivery: it announces "please deliver", notification does the sending.
 *
 * @param tenantId   owning tenant
 * @param userId     recipient user
 * @param campaignId the launched campaign
 * @param channel    simulation channel name (e.g. EMAIL, SMS, QR)
 * @param trackPath  relative tracking path ({@code /api/v1/sim/track/{token}})
 */
public record SimulationDeliveryRequestedEvent(
        UUID tenantId,
        UUID userId,
        UUID campaignId,
        String channel,
        String trackPath) {
}
