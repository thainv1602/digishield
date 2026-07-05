plugins {
    id("digishield.spring-module-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // TenantContext, for the @PreAuthorize tenant-match guard (hybrid tenancy authz).
    implementation(project(":shared:tenant-context"))
}
