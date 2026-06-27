/**
 * Risk analytics and benchmarking module.
 * <p>
 * Computes risk scores by scope (user / department / organization) and emits
 * {@code RiskRecomputedEvent}. Also listens for
 * {@code PhishingReportConfirmedEvent} to flag that risk needs recomputing.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Analytics",
        allowedDependencies = {
                "contracts :: events",
                "contracts :: dto",
                "shared :: tenant-context",
                "shared :: messaging"
        }
)
package com.digishield.analytics;
