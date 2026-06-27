package com.digishield.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * View of an AI-generated simulation template, matching the OpenAPI
 * {@code SimTemplate} schema. Enum-backed fields are emitted as lowercase
 * strings to match the frontend / spec.
 *
 * @param id         template identifier
 * @param channel    delivery channel (lowercase, e.g. "email")
 * @param subject    subject / hook line of the template
 * @param bodyRef    reference to the rendered body content (emitted as {@code body_ref})
 * @param difficulty difficulty level (lowercase: easy|medium|hard)
 * @param status     approval status (lowercase: draft|approved)
 */
public record SimTemplateView(
        @JsonProperty("id") UUID id,
        @JsonProperty("channel") String channel,
        @JsonProperty("subject") String subject,
        @JsonProperty("body_ref") String bodyRef,
        @JsonProperty("difficulty") String difficulty,
        @JsonProperty("status") String status) {
}
