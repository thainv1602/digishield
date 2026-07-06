package com.digishield.tenancy.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.UUID;

/**
 * Public view of a group (including smart groups), matching the OpenAPI
 * {@code Group} schema. Also used as the request body for {@code POST /groups}.
 *
 * @param id          group identifier (ignored on create)
 * @param name        display name
 * @param ruleJson    dynamic membership conditions of a smart group ({@code null}
 *                    for a static group)
 * @param memberCount materialised membership count ({@code null} when not yet
 *                    evaluated); read-only — ignored on create (the server
 *                    computes it from the rule)
 */
public record GroupView(
        @JsonProperty("id") UUID id,
        @JsonProperty("name") String name,
        @JsonProperty("rule_json") Map<String, Object> ruleJson,
        @JsonProperty("member_count") Integer memberCount) {
}
