package com.digishield.learning.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Aggregated (anonymized) results of an assessment
 * ({@code GET /assessments/{id}/results}).
 *
 * @param responseCount number of submitted responses
 * @param summary       aggregated summary metrics keyed by metric name
 */
public record AssessmentResultsView(
        @JsonProperty("response_count") int responseCount,
        @JsonProperty("summary") Map<String, Object> summary) {
}
