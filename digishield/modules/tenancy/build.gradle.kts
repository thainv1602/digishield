plugins {
    id("digishield.spring-module-conventions")
}

dependencies {
    // Shared contracts between modules (event, dto).
    implementation(project(":contracts"))

    // Shared infrastructure libraries.
    implementation(project(":shared:tenant-context"))
}
