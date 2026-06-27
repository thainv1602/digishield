package com.digishield.analytics.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Benchmark of the organisation's phish-prone rate against the industry average,
 * matching the OpenAPI {@code /analytics/benchmark} response shape.
 *
 * @param orgPhishPronePct the organisation's phish-prone percentage
 * @param industryAvgPct   the industry average phish-prone percentage
 */
public record BenchmarkDto(
        @JsonProperty("org_phish_prone_pct") double orgPhishPronePct,
        @JsonProperty("industry_avg_pct") double industryAvgPct) {
}
