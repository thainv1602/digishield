package com.digishield.tenancy.api;

/**
 * Command to create a new tenant.
 *
 * @param name       display name
 * @param tier       isolation tier ("POOL" | "BRIDGE" | "SILO")
 * @param dataRegion data region
 * @param adminEmail first administrator. Optional, but a tenant created without
 *                   one has nobody who can sign into it: users are created in
 *                   the caller's own tenant, so there is no second step that
 *                   puts an administrator inside this one.
 */
public record CreateTenantCommand(String name, String tier, String dataRegion, String adminEmail) {
}
