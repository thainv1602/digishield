package com.digishield.tenancy.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.UUID;

/**
 * Public view of a service plan, matching the OpenAPI {@code Plan} schema.
 *
 * @param id       plan identifier
 * @param name     plan name (edu | business | gov)
 * @param limits   limits object (seats, emails, ai_calls)
 * @param features feature toggles object
 */
public record PlanView(
        @JsonProperty("id") UUID id,
        @JsonProperty("name") String name,
        @JsonProperty("limits") Map<String, Object> limits,
        @JsonProperty("features") Map<String, Object> features) {
}
