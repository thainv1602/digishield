package com.digishield.tenancy.api;

/**
 * Command to create a new tenant.
 *
 * @param name       display name
 * @param tier       isolation tier ("POOL" | "BRIDGE" | "SILO")
 * @param dataRegion data region
 */
public record CreateTenantCommand(String name, String tier, String dataRegion) {
}
