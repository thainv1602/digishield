plugins {
    id("digishield.spring-module-conventions")
}

dependencies {
    // Shared contracts (events, DTOs) between modules.
    implementation(project(":contracts"))

    // Shared infrastructure.
    implementation(project(":shared:tenant-context"))
    implementation(project(":shared:messaging"))
}

// ---------------------------------------------------------------------------
// BTL group 01 - 90% coverage gate for the new feature package only.
// Scoped to the group's package rather than the whole module, so it does not
// turn CI red over legacy code that has no tests yet. Remove this block if the
// class moves to a shared, module-wide gate.
// ---------------------------------------------------------------------------
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("com.digishield.reporting.blacklistvalidation")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}
