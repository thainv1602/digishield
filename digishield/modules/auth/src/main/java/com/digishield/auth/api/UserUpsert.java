package com.digishield.auth.api;

import java.util.UUID;

/**
 * Mutable user attributes accepted when creating, updating or importing users.
 *
 * <p>Mirrors the OpenAPI {@code UserInput} schema; any field may be {@code null}
 * for a partial update ({@code PATCH /users/{id}}).
 *
 * @param email        login email (required on create, may be {@code null} on patch)
 * @param role         snake_case role (e.g. {@code learner}); {@code null} keeps the current role
 * @param departmentId department / org-unit id (OpenAPI {@code department_id})
 * @param locale       preferred UI locale (e.g. {@code vi})
 */
public record UserUpsert(String email, String role, UUID departmentId, String locale) {
}
