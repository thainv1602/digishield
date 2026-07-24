// End-to-end (Selenium) test module.
//
// Test-only project (Page Objects + scenarios live under src/test). The suite
// drives a REAL browser against a running frontend (:5173) + backend (:8080),
// so it is GUARDED behind -De2e.enabled=true and never runs in the normal
// unit-test CI. Run explicitly:
//
//   ./gradlew :e2e:test -De2e.enabled=true -Dselenium.headless=true
//
plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.seleniumhq.selenium:selenium-java:4.27.0")
    testImplementation("org.assertj:assertj-core:3.27.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Forward the guard + headless flags to the test JVM.
    systemProperty("e2e.enabled", System.getProperty("e2e.enabled", "false"))
    systemProperty("selenium.headless", System.getProperty("selenium.headless", "true"))
    systemProperty("e2e.baseUrl", System.getProperty("e2e.baseUrl", "http://localhost:5173"))
    systemProperty("e2e.apiUrl", System.getProperty("e2e.apiUrl", "http://localhost:8080/api/v1"))
    testLogging { events("passed", "skipped", "failed") }
}
