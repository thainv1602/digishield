plugins {
    `java-library`
    checkstyle
    jacoco
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
                // TODO raise threshold as coverage grows (target 0.50).
                minimum = "0.10".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
