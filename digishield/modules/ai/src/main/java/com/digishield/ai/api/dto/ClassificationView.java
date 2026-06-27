package com.digishield.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of classifying a reported email, matching the response schema of
 * {@code POST /ai/classify}.
 *
 * @param label      classification label (clean|spam|threat)
 * @param confidence model confidence in the range 0..1
 * @param reason     short human-readable justification for the label
 */
public record ClassificationView(
        @JsonProperty("label") String label,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("reason") String reason) {
}
