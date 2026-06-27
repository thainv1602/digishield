package com.digishield.tenancy.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Public view of a usage-metering data point, matching the OpenAPI
 * {@code UsageMetering} schema.
 *
 * @param tenantId owning tenant id
 * @param metric   email_sent | sms_sent | ai_call | storage
 * @param value    metered value
 * @param period   billing period, e.g. "2026-06"
 */
public record UsageMeteringView(
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("metric") String metric,
        @JsonProperty("value") long value,
        @JsonProperty("period") String period) {
}
