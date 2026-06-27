package com.digishield.tenancy.api;

import java.util.List;
import java.util.UUID;

/**
 * Public API of the Tenancy module.
 */
public interface TenancyService {

    /**
     * Create a new tenant.
     *
     * @param command information of the tenant to create
     * @return view of the newly created tenant
     */
    TenantView createTenant(CreateTenantCommand command);

    /**
     * Lists all tenants (Super Admin / Super Tenant Console).
     */
    List<TenantView> listTenants();

    /**
     * Lists the audit-log entries of a tenant, newest first.
     */
    List<AuditLogView> listAuditLogs(UUID tenantId);

    /**
     * Gets the SCIM / SSO configuration of a tenant ({@code null} if none).
     */
    ScimConfigView getScimConfig(UUID tenantId);

    /**
     * Get the list of feature flags of a tenant.
     *
     * @param tenantId business identifier of the tenant
     */
    List<FeatureFlagView> getFeatureFlags(UUID tenantId);

    /**
     * Check whether a feature flag is enabled for the tenant.
     *
     * @param tenantId business identifier of the tenant
     * @param key      key of the flag
     */
    boolean isEnabled(UUID tenantId, String key);
}
