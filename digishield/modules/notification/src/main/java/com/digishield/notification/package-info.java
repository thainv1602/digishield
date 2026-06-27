/**
 * Notification module: creates and sends notifications (in-app, email, sms) to users.
 * <p>
 * This is a Spring Modulith application module. The module listens to business events
 * (e.g. {@code EnrollmentAssignedEvent}) to generate reminder notifications. Inter-module
 * dependencies are declared explicitly: only dependencies on the shared library
 * (shared) and the contracts package (contracts) are allowed.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Notification",
        allowedDependencies = {
                "shared :: tenant-context",
                "contracts :: events",
                "contracts :: dto"
        }
)
package com.digishield.notification;
