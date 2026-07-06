package com.digishield.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * View of a simulation template, matching the OpenAPI {@code SimTemplate} schema.
 * Enum-backed fields are emitted as lowercase strings to match the frontend / spec.
 *
 * @param id         template identifier
 * @param channel    delivery channel (lowercase, e.g. "email")
 * @param subject    subject / hook line of the template
 * @param bodyRef    stable slug/reference to the rendered body (emitted as {@code body_ref})
 * @param body       the actual message body (the phishing email/SMS content)
 * @param category   free-text theme (e.g. "Cơ quan thuế"); may be {@code null}
 * @param difficulty difficulty level (lowercase: easy|medium|hard)
 * @param status     approval status (lowercase: draft|approved)
 */
public record SimTemplateView(
        @JsonProperty("id") UUID id,
        @JsonProperty("channel") String channel,
        @JsonProperty("subject") String subject,
        @JsonProperty("body_ref") String bodyRef,
        @JsonProperty("body") String body,
        @JsonProperty("category") String category,
        @JsonProperty("difficulty") String difficulty,
        @JsonProperty("status") String status) {
}
