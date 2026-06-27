package com.digishield.reporting.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Result of converting a threat-intel record into de-identified training
 * content. Matches the inline OpenAPI response of
 * {@code POST /threat-intel/{id}/convert} (snake_case wire names).
 *
 * @param templateId     identifier of the generated phishing template draft
 * @param coachingPageId identifier of the generated coaching page draft
 */
public record ThreatIntelConvertResultDto(
        @JsonProperty("template_id") UUID templateId,
        @JsonProperty("coaching_page_id") UUID coachingPageId) {
}
