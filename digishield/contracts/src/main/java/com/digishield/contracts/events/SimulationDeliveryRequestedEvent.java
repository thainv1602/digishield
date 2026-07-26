package com.digishield.contracts.events;

import java.util.UUID;

/**
 * Emitted once per recipient when a simulation campaign is launched, asking the
 * notification module to actually deliver that recipient's tracking link over
 * the campaign's channel (email/SMS). Keeps the simulation module decoupled from
 * delivery: it announces "please deliver", notification does the sending.
 *
 * <p>The campaign's template content travels on the event rather than being
 * looked up by the notification module, so notification stays a dumb transport
 * with no dependency on the template library. The three template fields are
 * {@code null} when the campaign has no template (or it could not be resolved);
 * the listener then falls back to a generic message.
 *
 * @param tenantId   owning tenant
 * @param userId     recipient user
 * @param campaignId the launched campaign
 * @param channel    simulation channel name (e.g. EMAIL, SMS, QR)
 * @param trackPath  relative tracking path ({@code /api/v1/sim/track/{token}})
 * @param subject    template subject, used as the message title; may be {@code null}
 * @param body       template body, possibly containing the {@code {{link}}}
 *                   placeholder; may be {@code null}
 * @param bodyFormat {@code "text"} or {@code "html"}; may be {@code null}
 */
public record SimulationDeliveryRequestedEvent(
        UUID tenantId,
        UUID userId,
        UUID campaignId,
        String channel,
        String trackPath,
        String subject,
        String body,
        String bodyFormat) {
}
