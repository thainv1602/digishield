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
    ":e2e",
)
