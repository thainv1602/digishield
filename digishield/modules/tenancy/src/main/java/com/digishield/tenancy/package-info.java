/**
 * Tenancy module: manages tenants (organization customers), tiers,
 * data regions and the feature flags of each tenant.
 * <p>
 * It is a Spring Modulith application module; it is only allowed to depend on the
 * shared libraries and the contracts package.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Tenancy",
        allowedDependencies = {
                "shared :: tenant-context",
                "contracts"
        }
)
package com.digishield.tenancy;
