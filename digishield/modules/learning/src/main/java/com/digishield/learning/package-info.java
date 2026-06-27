/**
 * Learning module: manages courses, enrollments, and the workflow for
 * automatically assigning a course when a user clicks a phishing simulation link.
 * <p>
 * A Spring Modulith application module; only allowed to depend on the shared
 * libraries and the contracts package. Communication with other modules is done
 * via application events in the contracts package.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Learning",
        allowedDependencies = {
                "shared :: tenant-context",
                "contracts :: events",
                "contracts :: dto"
        }
)
package com.digishield.learning;
