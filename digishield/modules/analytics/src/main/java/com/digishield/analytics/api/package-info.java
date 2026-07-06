/**
 * Public API of the analytics module, including the {@code RecentReportsProvider}
 * SPI. Exposed as a Spring Modulith named interface so the application shell can
 * supply a concrete implementation (bridged to the reporting module).
 */
@org.springframework.modulith.NamedInterface("api")
package com.digishield.analytics.api;
