package com.digishield.tenancy.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.UUID;

/**
 * Tenant configuration view/command, matching the OpenAPI {@code TenantSettings}
 * schema. Used for both {@code GET} and {@code PATCH /tenants/{id}/settings}.
 *
 * @param tenantId      owning tenant id
 * @param branding      branding object (logo, colors, subdomain)
 * @param policy        policy object (risk thresholds, mandatory training)
 * @param defaultLocale default locale, e.g. "vi"
 */
public record TenantSettingsView(
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("branding") Map<String, Object> branding,
        @JsonProperty("policy") Map<String, Object> policy,
        @JsonProperty("default_locale") String defaultLocale) {
}
