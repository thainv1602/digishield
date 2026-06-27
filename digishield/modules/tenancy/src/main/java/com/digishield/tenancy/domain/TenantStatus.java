package com.digishield.tenancy.domain;

/**
 * Lifecycle status of a tenant.
 */
public enum TenantStatus {
    /** Currently being provisioned. */
    PROVISIONING,
    /** Active. */
    ACTIVE,
    /** Suspended. */
    SUSPENDED,
    /** Deactivated. */
    DEACTIVATED
}
