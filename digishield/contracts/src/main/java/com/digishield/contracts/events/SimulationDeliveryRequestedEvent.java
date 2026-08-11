package com.digishield.contracts.events;

import java.util.List;
import java.util.UUID;

/**
 * Asks the notification module to deliver one campaign's lure to its recipients.
 *
 * <p>Carries the whole audience rather than one event per person. Spring Modulith
 * persists every published event, so a per-recipient event meant a thousand-row
 * write for a thousand-recipient campaign: measured, that was 3.1 of the 3.2
 * seconds such a send took, against 0.1 s for all the campaign's own inserts.
 *
 * <p>The lure is identical for everyone -- only the tracking path differs -- so
 * the shared content sits at the top level and {@link Recipient} holds the part
 * that varies.
 *
 * @param tenantId   owning tenant
 * @param campaignId the campaign being sent
 * @param channel    delivery channel name, e.g. {@code EMAIL}
 * @param subject    lure subject, may be null
 * @param body       lure body, may be null
 * @param bodyFormat body format, may be null
 * @param recipients who to deliver to, and the tracking path unique to each
 */
public record SimulationDeliveryRequestedEvent(
        UUID tenantId,
        UUID campaignId,
        String channel,
        String subject,
        String body,
        String bodyFormat,
        List<Recipient> recipients) {

    /**
     * One addressee of the send.
     *
     * @param userId    who receives it
     * @param trackPath the tracking path unique to this recipient
     */
    public record Recipient(UUID userId, String trackPath) {
    }
}
