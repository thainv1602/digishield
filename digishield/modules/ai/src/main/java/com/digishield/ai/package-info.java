/**
 * AI module: generates content (phishing/training templates), classifies reports, moderates
 * and orchestrates AI tasks.
 * <p>
 * This is a Spring Modulith application module. Inter-module dependencies are declared
 * explicitly: the shared libraries (tenant-context, messaging) and the contracts events.
 * AIDA orchestration publishes {@code AidaOrchestrationRequestedEvent} and finalises a run
 * on {@code AidaOrchestrationCompletedEvent}; classification/generation delegate to an AiClient.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "AI",
        allowedDependencies = {
                "shared :: tenant-context",
                "shared :: messaging",
                "contracts :: events"
        }
)
package com.digishield.ai;
