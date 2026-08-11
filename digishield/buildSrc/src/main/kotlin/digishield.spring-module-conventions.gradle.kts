plugins {
    `java-library`
    checkstyle
    jacoco
    id("com.github.spotbugs")
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
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.1.0")
    }
}

dependencies {
    "implementation"("org.springframework.boot:spring-boot-starter")
    "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
    "implementation"("org.springframework:spring-web")

    // Method-security annotations (@PreAuthorize) on controllers. Just the core
    // library — the resource server / filter chain live in the boot app + shared:security.
    "implementation"("org.springframework.security:spring-security-core")
    "implementation"("org.springframework.modulith:spring-modulith-starter-core")
    "implementation"("org.springframework.modulith:spring-modulith-events-api")

    // Jackson for DTO wire-format mapping (@JsonProperty) and JSON (rule_json,
    // settings, ...) serialization in services. Versions managed by the BOM.
    "implementation"("com.fasterxml.jackson.core:jackson-annotations")
    "implementation"("com.fasterxml.jackson.core:jackson-databind")

    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testImplementation"("org.springframework.modulith:spring-modulith-starter-test")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ---------------------------------------------------------------------------
// JaCoCo code coverage.
//
// Coverage exclusions: generated / boilerplate that is not meaningfully
// unit-tested (Application classes, Spring config, package-info, ...). The
// service `application/**` classes are deliberately KEPT in scope -- that is
// where the unit tests exercise behaviour.
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
                // A ratchet, not a target. The floor sits just under the least
                // covered subproject so it catches a module sliding backwards
                // without failing the build today. Raised to 0.30 once the
                // weakest, modules/tenancy, reached 32.8% — groups and the audit
                // trail were the untested parts. Raise it again as the next
                // weakest improves; the long-term target is still 0.50.
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
