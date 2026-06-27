package com.digishield.learning.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Wire view of a {@code CoachingPage} (OpenAPI {@code CoachingPage} schema).
 * Serializes/deserializes with snake_case field names.
 *
 * @param id          coaching page identifier (may be null on create)
 * @param templateId  associated simulation template id (may be null)
 * @param contentRef  reference to the coaching content
 * @param signalsJson recognizable warning signs as a JSON object
 */
public record CoachingPageView(
        @JsonProperty("id") UUID id,
        @JsonProperty("template_id") UUID templateId,
        @JsonProperty("content_ref") String contentRef,
        @JsonProperty("signals_json") Object signalsJson) {
}
