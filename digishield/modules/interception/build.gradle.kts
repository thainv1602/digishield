plugins {
    id("digishield.spring-module-conventions")
}

dependencies {
    // Shared contracts between modules (events, dtos).
    implementation(project(":contracts"))

    // Shared infrastructure library.
    implementation(project(":shared:tenant-context"))
}
