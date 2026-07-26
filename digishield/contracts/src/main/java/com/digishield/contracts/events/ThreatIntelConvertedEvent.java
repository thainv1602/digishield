package com.digishield.contracts.events;

import java.util.UUID;

/**
 * Emitted when a SOC analyst converts a threat-intel record into training
 * content ("ThreatFlip"). The learning module consumes it to create the actual
 * coaching page with the supplied id, so the reporting module can return a
 * coaching-page id that references real content instead of a dangling one.
 *
 * @param tenantId       owning tenant
 * @param coachingPageId id the coaching page must be created with
 * @param templateId     the de-identified template id linked on the intel
 * @param contentRef     content reference/source for the coaching page
 */
public record ThreatIntelConvertedEvent(
        UUID tenantId,
        UUID coachingPageId,
        UUID templateId,
        String contentRef) {
}
