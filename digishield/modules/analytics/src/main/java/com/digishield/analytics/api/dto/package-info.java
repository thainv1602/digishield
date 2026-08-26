/**
 * Public DTOs of the analytics module (e.g. {@code DashboardDto}). Exposed as a
 * Spring Modulith named interface so callers of the analytics API can consume
 * the returned views across the module boundary — the application shell binds
 * {@code DashboardDto} when it configures how the dashboard cache serialises
 * its entries.
 */
@org.springframework.modulith.NamedInterface("dto")
package com.digishield.analytics.api.dto;
