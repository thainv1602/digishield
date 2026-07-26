plugins {
    id("digishield.spring-module-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // DispatcherType, for the ERROR-dispatch authorization rule. Provided by the
    // servlet container at runtime, so compile-only.
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    // TenantContext, for the @PreAuthorize tenant-match guard (hybrid tenancy authz).
    implementation(project(":shared:tenant-context"))
}
