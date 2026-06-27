package com.digishield.learning.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Recommended starting level from an adaptive placement assessment
 * ({@code POST /assessments/placement}).
 *
 * @param level recommended level (basic|beginner|intermediate|advanced)
 */
public record PlacementResultView(
        @JsonProperty("level") String level) {
}
