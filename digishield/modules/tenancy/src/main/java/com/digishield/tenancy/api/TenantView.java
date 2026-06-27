package com.digishield.tenancy.api;

import java.util.UUID;

/**
 * Public view describing a tenant (Super Tenant Console).
 *
 * @param id         tenant identifier
 * @param tenantId   business identifier of the tenant
 * @param name       display name
 * @param tier       isolation tier (pool|bridge|silo)
 * @param dataRegion data region
 * @param status     lifecycle status (provisioning|active|suspended|deactivated)
 * @param userCount  number of users in the tenant (may be null)
 * @param domain     primary domain of the tenant (may be null)
 */
public record TenantView(UUID id, UUID tenantId, String name, String tier,
                         String dataRegion, String status, Integer userCount, String domain) {
}
