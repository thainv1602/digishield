/**
 * Public API of the tenancy module, including the audit-log write path. Exposed
 * as a Spring Modulith named interface so the application shell can bridge it to
 * the {@code AuditRecorder} SPI that every other module writes through.
 */
@org.springframework.modulith.NamedInterface("api")
package com.digishield.tenancy.api;
