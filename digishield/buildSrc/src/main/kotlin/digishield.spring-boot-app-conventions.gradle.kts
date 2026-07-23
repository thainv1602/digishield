plugins {
    java
    checkstyle
    jacoco
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

checkstyle {
    toolVersion = "10.21.0"
    configDirectory.set(rootProject.layout.projectDirectory.dir("config/checkstyle"))
    configFile = rootProject.layout.projectDirectory.file("config/checkstyle/checkstyle.xml").asFile
    // Make the suppressions file path available to checkstyle.xml.
    configProperties["checkstyle.suppressions.file"] =
        rootProject.layout.projectDirectory.file("config/checkstyle/suppressions.xml").asFile.absolutePath
    maxWarnings = 0
    // TODO flip to false to enforce
    isIgnoreFailures = true
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
                // TODO raise threshold as coverage grows (target 0.50).
                minimum = "0.10".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
