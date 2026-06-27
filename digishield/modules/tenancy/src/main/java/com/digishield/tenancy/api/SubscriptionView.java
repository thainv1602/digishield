package com.digishield.tenancy.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Public view of a tenant's subscription, matching the OpenAPI
 * {@code Subscription} schema.
 *
 * @param id       subscription identifier
 * @param tenantId owning tenant id
 * @param planId   subscribed plan id
 * @param status   trial | active | past_due | canceled
 * @param renewsAt next renewal date
 */
public record SubscriptionView(
        @JsonProperty("id") UUID id,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("plan_id") UUID planId,
        @JsonProperty("status") String status,
        @JsonProperty("renews_at") LocalDate renewsAt) {
}
