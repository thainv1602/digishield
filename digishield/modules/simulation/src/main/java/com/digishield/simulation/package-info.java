/**
 * Phishing attack simulation module.
 * <p>
 * Manages multi-channel simulation campaigns and records user interaction
 * events. When a user clicks a simulation link, the module emits
 * {@code UserClickedSimulationEvent} for other modules (e.g. analytics)
 * to consume.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Simulation",
        allowedDependencies = {
                "contracts :: events",
                "contracts :: dto",
                "shared :: tenant-context",
                "shared :: messaging"
        }
)
package com.digishield.simulation;
