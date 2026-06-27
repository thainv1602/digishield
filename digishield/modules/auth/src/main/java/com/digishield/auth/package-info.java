/**
 * Auth module: manages application users, roles, and querying the current user.
 * <p>
 * This is a Spring Modulith application module. The inter-module dependencies are
 * declared explicitly below: only dependencies on the shared libraries
 * (shared) and the contracts package (contracts) are allowed.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Auth",
        allowedDependencies = {
                "shared :: tenant-context",
                "shared :: security",
                "contracts"
        }
)
package com.digishield.auth;
