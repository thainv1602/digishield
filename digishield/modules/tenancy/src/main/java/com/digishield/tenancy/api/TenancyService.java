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

    /**
     * Gets a single tenant by its id ({@code null} if not found).
     */
    TenantView getTenant(UUID id);

    /**
     * Updates a tenant's tier, status and/or data region (only non-null fields).
     */
    TenantView updateTenant(UUID id, UpdateTenantCommand command);

    /**
     * Gets the configuration (branding/policy/locale) of a tenant, creating an
     * empty default if none exists yet.
     */
    TenantSettingsView getTenantSettings(UUID tenantId);

    /**
     * Updates the configuration (branding/policy/locale) of a tenant.
     */
    TenantSettingsView updateTenantSettings(UUID tenantId, TenantSettingsView settings);

    /**
     * Gets the business thresholds of a tenant, creating sensible defaults if
     * none exist yet.
     */
    BusinessThresholdsView getThresholds(UUID tenantId);

    /**
     * Updates a tenant's business thresholds; null fields are left unchanged.
     */
    BusinessThresholdsView updateThresholds(UUID tenantId, BusinessThresholdsView command);

    /**
     * Enables or disables a feature flag for a tenant, creating it if absent.
     */
    FeatureFlagView setFeatureFlag(UUID tenantId, String key, boolean enabled);

    /**
     * Lists the groups (incl. smart groups) of the current tenant.
     */
    List<GroupView> listGroups(UUID tenantId);

    /**
     * Creates a group for the given tenant.
     */
    GroupView createGroup(UUID tenantId, GroupView group);

    /**
     * Re-evaluates a smart group and returns the resulting member count.
     */
    MemberCountView evaluateGroup(UUID tenantId, UUID groupId);

    /**
     * Updates a group's name and/or rule (null fields left unchanged; pass an
     * empty rule to turn a smart group into a static one).
     */
    GroupView updateGroup(UUID tenantId, UUID groupId, GroupView command);

    /**
     * Deletes a group and its memberships.
     */
    void deleteGroup(UUID tenantId, UUID groupId);

    /**
     * Returns the user ids that belong to a group.
     */
    List<UUID> listGroupMembers(UUID tenantId, UUID groupId);

    /**
     * Adds a user to a group (no-op if already a member); returns the new count.
     */
    MemberCountView addGroupMember(UUID tenantId, UUID groupId, UUID userId);

    /**
     * Removes a user from a group; returns the new count.
     */
    MemberCountView removeGroupMember(UUID tenantId, UUID groupId, UUID userId);

    /**
     * Lists the usage-metering data points of a tenant, optionally filtered by
     * billing period.
     */
    List<UsageMeteringView> getUsage(UUID tenantId, String period);

    /**
     * Gets the current subscription of a tenant ({@code null} if none).
     */
    SubscriptionView getSubscription(UUID tenantId);

    /**
     * Changes the plan of a tenant's subscription, creating one if absent.
     */
    SubscriptionView changeSubscription(UUID tenantId, UUID planId);

    /**
     * Lists the global service plans.
     */
    List<PlanView> listPlans();
}
