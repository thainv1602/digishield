package com.digishield.auth.api;

import java.util.UUID;

/**
 * Immutable view (public API) describing the logged-in user of the current request.
 * <p>
 * This is the type exposed to other modules, decoupled from the internal JPA entity.
 *
 * @param id       the user identifier
 * @param tenantId the tenant the user belongs to
 * @param email    the login email
 * @param role     the role as the internal enum name (e.g. {@code ORG_ADMIN});
 *                 the web layer maps it to the snake_case wire form for the API
 * @param name     the display name (may be {@code null})
 */
public record CurrentUser(UUID id, UUID tenantId, String email, String role, String name) {
}
