package com.digishield.analytics.api;

import java.util.List;
import java.util.UUID;

/**
 * SPI listing the tenants a scheduled rollup must visit.
 * <p>
 * A scheduled job has no request and therefore no tenant of its own, so it
 * cannot discover its own work — it has to be told which tenants exist before it
 * can set a tenant context and compute under normal isolation.
 */
public interface TenantDirectory {

    /** Tenant ids of every active tenant, across the platform. */
    List<UUID> activeTenantIds();
}
