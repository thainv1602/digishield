package com.digishield.tenancy.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Request body for {@code PUT /tenants/{id}/subscription} (change plan).
 *
 * @param planId identifier of the plan to switch to
 */
public record ChangePlanCommand(
        @JsonProperty("plan_id") UUID planId) {
}
