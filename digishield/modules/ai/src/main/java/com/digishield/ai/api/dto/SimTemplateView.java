package com.digishield.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * View of a simulation template, matching the OpenAPI {@code SimTemplate} schema.
 * Enum-backed fields are emitted as lowercase strings to match the frontend / spec.
 *
 * @param id          template identifier
 * @param channel     delivery channel (lowercase, e.g. "email")
 * @param subject     subject / hook line of the template
 * @param bodyRef     stable slug/reference to the rendered body (emitted as {@code body_ref})
 * @param body        the actual message body (the phishing email/SMS content)
 * @param bodyFormat  how the body is rendered (lowercase: text|html)
 * @param category    free-text theme (e.g. "Cơ quan thuế"); may be {@code null}
 * @param logoUrl     impersonated brand logo URL/data-URI (emitted as {@code logo_url}); may be {@code null}
 * @param attachments simulated attachment metadata (never {@code null}; empty if none)
 * @param difficulty  difficulty level (lowercase: easy|medium|hard)
 * @param status      approval status (lowercase: draft|approved)
 */
public record SimTemplateView(
        @JsonProperty("id") UUID id,
        @JsonProperty("channel") String channel,
        @JsonProperty("subject") String subject,
        @JsonProperty("body_ref") String bodyRef,
        @JsonProperty("body") String body,
        @JsonProperty("body_format") String bodyFormat,
        @JsonProperty("category") String category,
        @JsonProperty("logo_url") String logoUrl,
        @JsonProperty("attachments") List<AttachmentView> attachments,
        @JsonProperty("difficulty") String difficulty,
        @JsonProperty("status") String status) {
}
