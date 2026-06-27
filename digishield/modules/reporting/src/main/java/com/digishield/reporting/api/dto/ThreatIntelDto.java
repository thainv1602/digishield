package com.digishield.reporting.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Threat-intel view matching the OpenAPI {@code ThreatIntel} schema
 * (snake_case wire names). Also used as the ingest request body.
 *
 * @param id                  intel identifier
 * @param source              origin of the intel (e.g. "NCSC")
 * @param rawPayload          raw payload of the threat sample
 * @param convertedTemplateId template generated if/when converted (nullable)
 * @param collectedAt         when the intel was collected
 */
public record ThreatIntelDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("source") String source,
        @JsonProperty("raw_payload") String rawPayload,
        @JsonProperty("converted_template_id") UUID convertedTemplateId,
        @JsonProperty("collected_at") Instant collectedAt) {
}
