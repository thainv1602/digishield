plugins {
    id("digishield.spring-boot-app-conventions")
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Redis-backed caching. Connection from spring.data.redis.* (env
    // REDIS_HOST/PORT/PASSWORD/SSL). The dev profile disables it (no Redis).
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // Jackson ObjectMapper for the shared JacksonConfig bean (modules inject it
    // for JSON columns). Spring Boot 4 does not expose one on the app classpath
    // by default.
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Spring Modulith
    implementation("org.springframework.modulith:spring-modulith-starter-core")

    // Persistence / migration
    runtimeOnly("org.postgresql:postgresql")
    // H2 in-memory database for the `dev` profile (schema built from JPA entities,
    // no Flyway). Production keeps using PostgreSQL + Flyway, unchanged.
    runtimeOnly("com.h2database:h2")
    implementation("org.flywaydb:flyway-core")
    // Flyway 10+ (Spring Boot 4.x) splits per-database support into modules; the
    // PostgreSQL one is required or Flyway silently no-ops and JPA schema
    // validation then fails against an unmigrated database.
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    // Spring Boot 4.x moved FlywayAutoConfiguration into its own module. Without
    // it, flyway-core is on the classpath but Spring Boot never runs migrations
    // (silent no-op), so JPA ddl-auto=validate fails on an unmigrated database.
    implementation("org.springframework.boot:spring-boot-flyway")

    // AWS SES (email notifications). Active only when
    // digishield.notifications.email.ses.enabled=true; credentials via the
    // default provider chain (IRSA in-cluster).
    implementation(platform("software.amazon.awssdk:bom:2.31.6"))
    implementation("software.amazon.awssdk:sesv2")
    implementation("software.amazon.awssdk:cognitoidentityprovider")

    // Contracts
    implementation(project(":contracts"))

    // Shared libraries
    implementation(project(":shared:persistence"))
    implementation(project(":shared:tenant-context"))
    implementation(project(":shared:messaging"))
    implementation(project(":shared:security"))
    implementation(project(":shared:observability"))

    // Business modules (all 9 - so Modulith can scan them)
    implementation(project(":modules:auth"))
    implementation(project(":modules:tenancy"))
    implementation(project(":modules:learning"))
    implementation(project(":modules:simulation"))
    implementation(project(":modules:reporting"))
    implementation(project(":modules:analytics"))
    implementation(project(":modules:notification"))
    implementation(project(":modules:ai"))
    implementation(project(":modules:interception"))

    // Unit tests live in src/test (no Docker): the fast Modulith verification
    // (ModularityTests) plus any *Test.java slices. spring-boot-starter-test
    // already provides JUnit5 / Mockito / AssertJ.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ---------------------------------------------------------------------------
// Test taxonomy: separate unit tests (UT) from integration tests (IT).
//
//   * `test`            -> src/test           -> *Test.java, pure / no Docker.
//   * `integrationTest` -> src/integrationTest -> *IT.java, Spring context /
//                          Testcontainers (needs a running Docker daemon).
//
// JVM Test Suites give the IT suite its own source set, classpath and task.
// ---------------------------------------------------------------------------
testing {
    suites {
        // Keep the default unit-test suite on JUnit Jupiter.
        getByName<JvmTestSuite>("test") {
            useJUnitJupiter()
        }

        // Integration test suite: Spring context + Testcontainers (Docker).
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()

            dependencies {
                // Import dependency management BOMs as platforms
                implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
                implementation(platform("org.springframework.modulith:spring-modulith-bom:2.1.0"))

                // Depend on the boot app's own production classes.
                implementation(project())

                // Spring + Modulith test support.
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("org.springframework.boot:spring-boot-starter-webmvc-test")
                implementation("org.springframework.modulith:spring-modulith-starter-test")

                // Testcontainers (tenant-isolation / simulation ITs need Docker).
                implementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
                implementation("org.springframework.boot:spring-boot-testcontainers")
                implementation("org.testcontainers:junit-jupiter")
                implementation("org.testcontainers:postgresql")
                runtimeOnly("org.junit.platform:junit-platform-launcher")
            }

            targets {
                all {
                    // Run the (slow, Docker-bound) ITs only after the fast UTs.
                    testTask.configure {
                        shouldRunAfter(tasks.named("test"))
                    }
                }
            }
        }
    }
}

// Inherit dependencies from main configurations for integration tests
configurations {
    named("integrationTestImplementation") {
        extendsFrom(configurations.implementation.get())
    }
    named("integrationTestRuntimeOnly") {
        extendsFrom(configurations.runtimeOnly.get())
    }
}

// `check` must run BOTH unit and integration tests (+ checkstyle + jacoco).
tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}

tasks.jacocoTestCoverageVerification {
    enabled = false
}
