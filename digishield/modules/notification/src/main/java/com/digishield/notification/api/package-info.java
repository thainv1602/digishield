/**
 * Public API of the notification module, including the {@code NotificationGateway}
 * and {@code RecipientResolver} SPIs. Exposed as a Spring Modulith named interface
 * so the application shell can supply concrete implementations (e.g. AWS SES).
 */
@org.springframework.modulith.NamedInterface("api")
package com.digishield.notification.api;
