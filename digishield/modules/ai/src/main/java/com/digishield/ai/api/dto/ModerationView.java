package com.digishield.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Result of safety-moderating AI-generated content, matching the response
 * schema of {@code POST /ai/moderate}.
 *
 * @param verdict moderation verdict (pass|flag|block)
 * @param reasons zero or more reasons explaining the verdict
 */
public record ModerationView(
        @JsonProperty("verdict") String verdict,
        @JsonProperty("reasons") List<String> reasons) {
}
