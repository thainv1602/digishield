plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.1.0")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:6.5.10")
}
