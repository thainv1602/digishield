import org.gradle.testing.jacoco.plugins.JacocoCoverageReport

plugins {
    // Aggregates JaCoCo coverage data from every subproject into one report.
    `jacoco-report-aggregation`
}

allprojects {
    group = "com.digishield"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

// ---------------------------------------------------------------------------
// Aggregated coverage report across all subprojects.
//
//   ./gradlew testCodeCoverageReport
//
// Output (HTML): build/reports/jacoco/testCodeCoverageReport/html/index.html
// Output (XML) : build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml
// ---------------------------------------------------------------------------
dependencies {
    // The subprojects manage versions with the io.spring.dependency-management
    // plugin, which applies versions only during each project's own resolution
    // and does NOT publish them as constraints. The aggregation resolves every
    // subproject's runtime classpath here at the root, so version-less deps
    // (e.g. micrometer-registry-prometheus) would resolve to an empty version.
    // Import the same BOMs as platforms on this configuration so they do.
    jacocoAggregation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    jacocoAggregation(platform("org.springframework.modulith:spring-modulith-bom:2.1.0"))

    jacocoAggregation(project(":boot:app"))
    jacocoAggregation(project(":contracts"))

    jacocoAggregation(project(":shared:persistence"))
    jacocoAggregation(project(":shared:tenant-context"))
    jacocoAggregation(project(":shared:messaging"))
    jacocoAggregation(project(":shared:security"))
    jacocoAggregation(project(":shared:observability"))

    jacocoAggregation(project(":modules:auth"))
    jacocoAggregation(project(":modules:tenancy"))
    jacocoAggregation(project(":modules:learning"))
    jacocoAggregation(project(":modules:simulation"))
    jacocoAggregation(project(":modules:reporting"))
    jacocoAggregation(project(":modules:analytics"))
    jacocoAggregation(project(":modules:notification"))
    jacocoAggregation(project(":modules:ai"))
    jacocoAggregation(project(":modules:interception"))
}

// The jacoco-report-aggregation plugin only auto-creates the
// `testCodeCoverageReport` task when the jvm-test-suite plugin is applied here.
// We aren't a test-suite project, so declare the report explicitly: it
// aggregates the `test` suite coverage from every jacocoAggregation dependency.
reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName.set("test")
        }
        // boot/app's integration suite is where the Spring context, the RLS
        // policies and the module wiring are actually exercised. Aggregating
        // only `test` reported the same number whether or not it had run.
        val integrationTestCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName.set("integrationTest")
        }
    }
}
