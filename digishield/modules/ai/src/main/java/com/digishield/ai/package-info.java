/**
 * AI module: generates content (phishing/training templates), classifies reports, moderates
 * and orchestrates AI tasks.
 * <p>
 * This is a Spring Modulith application module. Inter-module dependencies are declared
 * explicitly: only dependencies on the shared library (shared) and the contracts package
 * (contracts) are allowed. The current implementation returns hardcoded samples; the LLM call is marked TODO.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "AI",
        allowedDependencies = {
                "shared :: tenant-context",
                "contracts"
        }
)
package com.digishield.ai;
