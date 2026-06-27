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
