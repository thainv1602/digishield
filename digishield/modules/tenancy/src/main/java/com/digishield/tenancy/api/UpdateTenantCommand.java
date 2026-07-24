package com.digishield.tenancy.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for {@code PATCH /tenants/{id}} (update tier, status and/or data
 * region). All fields are optional; only non-null values are applied. Enum
 * values are accepted case-insensitively (matching the lowercase OpenAPI enums).
 *
 * @param name       new display name
 * @param tier       new isolation tier (pool | bridge | silo)
 * @param status     new lifecycle status (provisioning | active | suspended | offboarding)
 * @param dataRegion new data region
 */
public record UpdateTenantCommand(
        @JsonProperty("name") String name,
        @JsonProperty("tier") String tier,
        @JsonProperty("status") String status,
        @JsonProperty("data_region") String dataRegion) {
}
