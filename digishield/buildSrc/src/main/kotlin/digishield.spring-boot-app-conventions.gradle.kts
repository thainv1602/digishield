plugins {
    java
    checkstyle
    jacoco
    id("com.github.spotbugs")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// Spring resolves @PathVariable/@RequestParam names via reflection; without
// -parameters every unnamed binding fails at runtime with "parameter name
// information not available". The Boot plugin adds this only to the app
// project, so set it explicitly for every convention.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

checkstyle {
    toolVersion = "10.21.0"
    configDirectory.set(rootProject.layout.projectDirectory.dir("config/checkstyle"))
    configFile = rootProject.layout.projectDirectory.file("config/checkstyle/checkstyle.xml").asFile
    // Make the suppressions file path available to checkstyle.xml.
    configProperties["checkstyle.suppressions.file"] =
        rootProject.layout.projectDirectory.file("config/checkstyle/suppressions.xml").asFile.absolutePath
    maxWarnings = 0
    // Enforced: a Checkstyle violation fails the build. The two deliberate
    // conventions it used to trip over -- unit_scenario test method names and
    // the SLF4J `log` field -- are now expressed in config/checkstyle rather
    // than tolerated by ignoring every violation.
    isIgnoreFailures = false
}

// Ensure checkstyle runs as part of `check` (the checkstyle plugin already wires
// checkstyleMain / checkstyleTest into the check task by default).
tasks.named("check") {
    dependsOn(tasks.withType<Checkstyle>())
}

// Pin BOM-managed versions past fixable HIGH CVEs (Trivy image gate in cd.yml):
// netty 4.2.16 — CVE-2026-59901/55831/55833/56745; pgjdbc 42.7.12 — CVE-2026-54291.
extra["netty.version"] = "4.2.16.Final"
extra["postgresql.version"] = "42.7.12"

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.1.0")
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
    layered {
        enabled.set(true)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ---------------------------------------------------------------------------
// JaCoCo code coverage (boot app).
//
// jacocoTestReport covers the fast UNIT tests (`test` task / src/test). The
// integrationTest suite has its own coverage data; the root aggregated report
// (`testCodeCoverageReport`) stitches everything together.
//
// Exclusions: generated / boilerplate not meaningfully unit-tested
// (Application class, Spring config, package-info).
// ---------------------------------------------------------------------------
val jacocoExclusions = listOf(
    "**/*Application*",
    "**/config/**",
    "**/*Config*",
    "**/package-info*",
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map { dir ->
            fileTree(dir) { exclude(jacocoExclusions) }
        }),
    )
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(classDirectories.files.map { dir ->
            fileTree(dir) { exclude(jacocoExclusions) }
        }),
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                // A ratchet, not a target — see the same rule in
                // digishield.spring-module-conventions. Note this task measures
                // the `test` (unit) suite only, so boot/app scores 25.3% here
                // even though its integration tests exercise a good deal more.
                minimum = "0.30".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// ---------------------------------------------------------------------------
// SpotBugs -- bytecode analysis, complementing Checkstyle (style) and CodeQL
// (security). Main sources only: SpotBugs on test code is mostly noise about
// mock fields and assertions.
// ---------------------------------------------------------------------------
spotbugs {
    toolVersion = "4.10.3"
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.DEFAULT
    excludeFilter = rootProject.layout.projectDirectory.file("config/spotbugs/exclude.xml").asFile
    // Enforced from the start. Checkstyle spent months at ignoreFailures = true
    // and quietly accumulated 180 violations; a gate nobody can fail is not a
    // gate. Everything SpotBugs found on the first run is either fixed or
    // excluded with a reason in config/spotbugs/exclude.xml.
    ignoreFailures = false
}

tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsTest") {
    enabled = false
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("xml") { required.set(true) }
    reports.create("html") { required.set(true) }
}
