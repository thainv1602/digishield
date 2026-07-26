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
     * How much authority the role carries, for deciding who may edit whom.
     *
     * <p>Mirrors the hierarchy in {@code MethodSecurityConfig}. Manager, content
     * editor and analyst are peers rather than a chain, so they share a rank —
     * the ordering only has to answer "does this outrank that", not sort every
     * pair. {@code TENANT_ADMIN} is the legacy spelling of {@code ORG_ADMIN} and
     * ranks with it.
     */
    public int rank() {
        return switch (this) {
            case SUPER_ADMIN -> 40;
            case ORG_ADMIN, TENANT_ADMIN -> 30;
            case MANAGER, CONTENT_EDITOR, ANALYST -> 20;
            case LEARNER -> 10;
        };
    }

    /** True when this role may administer an account holding {@code other}. */
    public boolean outranksOrEquals(Role other) {
        return other == null || rank() >= other.rank();
    }

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
