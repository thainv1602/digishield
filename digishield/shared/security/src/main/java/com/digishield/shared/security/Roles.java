package com.digishield.shared.security;

/**
 * Shared role-name constants, kept in step with {@code Role} by
 * {@code RoleGuardConsistencyTests}.
 *
 * <p>Usable directly from Java, which is where they earn their keep:
 *
 * <pre>{@code .requestMatchers("/actuator/**").hasRole(Roles.SUPER_ADMIN)}</pre>
 *
 * <p>They cannot be referenced from inside a {@code @PreAuthorize} expression.
 * That argument is SpEL, not Java, so {@code hasRole(Roles.ORG_ADMIN)} reads as
 * a property lookup on the evaluation root and fails at request time rather than
 * at compile time. Either concatenate at compile time:
 *
 * <pre>{@code @PreAuthorize("hasRole('" + Roles.ORG_ADMIN + "')")}</pre>
 *
 * <p>or write the literal, as the controllers here do. A literal is unchecked by
 * the compiler and by Spring — a misspelt role silently rejects every caller —
 * so {@code RoleGuardConsistencyTests} scans the annotations and fails the build
 * on any name that is not a real role.
 */
public final class Roles {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ORG_ADMIN = "ORG_ADMIN";
    public static final String MANAGER = "MANAGER";
    public static final String CONTENT_EDITOR = "CONTENT_EDITOR";
    public static final String ANALYST = "ANALYST";
    public static final String LEARNER = "LEARNER";

    private Roles() {
    }
}
