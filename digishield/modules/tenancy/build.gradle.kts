plugins {
    id("digishield.spring-module-conventions")
}

dependencies {
    // Shared contracts between modules (event, dto).
    implementation(project(":contracts"))

    // Shared infrastructure libraries.
    implementation(project(":shared:tenant-context"))

    // HttpServletRequest, for the source IP on the impersonation audit entry.
    // Provided by the servlet container at runtime.
    compileOnly("jakarta.servlet:jakarta.servlet-api")
}
