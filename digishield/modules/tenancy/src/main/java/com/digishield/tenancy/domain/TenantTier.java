package com.digishield.tenancy.domain;

/**
 * Data isolation tier of a tenant.
 */
public enum TenantTier {
    /** Shared schema/database (pooled). */
    POOL,
    /** Shared database but separate schema (bridge). */
    BRIDGE,
    /** Fully separate database/infrastructure (silo). */
    SILO
}
