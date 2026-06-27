package com.digishield.auth.domain;

/**
 * Role of a user within the DigiShield system.
 *
 * <p>The wire/JSON form (used by the OpenAPI contract and the React frontend)
 * is the snake_case name returned by {@link #wireName()} — e.g. {@code org_admin},
 * {@code content_editor}. Use {@link #fromWireName(String)} to parse it back.
 */
public enum Role {
    /** System-level / provider administrator. */
    SUPER_ADMIN,
    /** Administrator of a tenant (a.k.a. {@code org_admin} on the wire). */
    ORG_ADMIN,
    /**
     * Legacy alias kept for backwards compatibility (older tests / data).
     * Treated as {@code org_admin} on the wire.
     */
    TENANT_ADMIN,
    /** Manager / coordinator within a tenant. */
    MANAGER,
    /** Content author for courses / coaching pages / templates. */
    CONTENT_EDITOR,
    /** SOC analyst triaging reported phishing. */
    ANALYST,
    /** End user (an employee being trained). */
    LEARNER;

    /**
     * The snake_case identifier used by the OpenAPI schema and the frontend.
     */
    public String wireName() {
        if (this == TENANT_ADMIN) {
            return "org_admin";
        }
        return name().toLowerCase();
    }

    /**
     * Parses a wire/JSON role name (snake_case, case-insensitive) into a {@link Role}.
     * Falls back to {@link #LEARNER} for unknown values.
     */
    public static Role fromWireName(String wire) {
        if (wire == null || wire.isBlank()) {
            return LEARNER;
        }
        String normalized = wire.trim().toUpperCase();
        for (Role role : values()) {
            if (role.name().equals(normalized) || role.wireName().equalsIgnoreCase(wire.trim())) {
                return role;
            }
        }
        return LEARNER;
    }
}
