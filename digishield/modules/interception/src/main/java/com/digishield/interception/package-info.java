/**
 * Interception module: transaction intervention SDK feature (specific to Vietnam).
 * <p>
 * Evaluates real-time signals (on a call, new payee, watchlist match, etc.)
 * to produce an intervention decision (ALLOW/WARN/PAUSE/BLOCK) with an educational message,
 * while also recording the intervention event.
 * <p>
 * This is a Spring Modulith application module. Inter-module dependencies are declared
 * explicitly: only dependencies on the shared library (shared) and the contracts package
 * (contracts) are allowed.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Interception",
        allowedDependencies = {
                "shared :: tenant-context",
                "contracts"
        }
)
package com.digishield.interception;
