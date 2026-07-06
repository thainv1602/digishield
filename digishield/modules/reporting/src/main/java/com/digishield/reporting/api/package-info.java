/**
 * Public API of the reporting module ({@code ReportingService} + friends).
 * Exposed as a Spring Modulith named interface so the application shell can read
 * the tenant's phishing reports (e.g. to feed the analytics dashboard).
 */
@org.springframework.modulith.NamedInterface("api")
package com.digishield.reporting.api;
