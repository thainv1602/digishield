rootProject.name = "digishield"

include(
    ":boot:app",
    ":contracts",
    ":shared:persistence",
    ":shared:tenant-context",
    ":shared:messaging",
    ":shared:security",
    ":shared:observability",
    ":modules:auth",
    ":modules:tenancy",
    ":modules:learning",
    ":modules:simulation",
    ":modules:reporting",
    ":modules:analytics",
    ":modules:notification",
    ":modules:ai",
    ":modules:interception",
)

// Test-only Selenium module. It is NOT copied into the Docker build context
// (the Dockerfile copies only the app's modules), so guard the include: image
// builds without e2e/ still configure, while a full checkout picks it up.
if (file("e2e").isDirectory) {
    include(":e2e")
}
