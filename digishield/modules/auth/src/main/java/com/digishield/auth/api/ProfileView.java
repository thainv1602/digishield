package com.digishield.auth.api;

import java.util.UUID;

/**
 * The signed-in user's own profile (self-service). Identity fields come from the
 * JWT / provisioned {@code app_user} row; {@code name} and {@code locale} are the
 * editable preferences persisted per account.
 */
public record ProfileView(UUID id, UUID tenantId, String email, String role, String name, String locale) {
}
