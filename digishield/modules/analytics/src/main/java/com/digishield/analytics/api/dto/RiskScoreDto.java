package com.digishield.analytics.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Risk score response matching the OpenAPI {@code RiskScore} schema
 * ({@code scope}, {@code scope_id}, {@code value}, {@code computed_at}).
 *
 * @param scope      scope of the score ("user" | "dept" | "org")
 * @param scopeId    identifier of the scope (may be null for an org-wide score)
 * @param value      score value in the range 0..100
 * @param computedAt when the score was computed
 */
public record RiskScoreDto(
        @JsonProperty("scope") String scope,
        @JsonProperty("scope_id") UUID scopeId,
        @JsonProperty("value") int value,
        @JsonProperty("computed_at") Instant computedAt) {
}
