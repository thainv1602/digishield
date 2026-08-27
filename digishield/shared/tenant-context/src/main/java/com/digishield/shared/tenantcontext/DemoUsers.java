package com.digishield.shared.tenantcontext;

import java.util.UUID;

/**
 * Well-known user identifiers seeded under {@link DemoTenants#DEMO_TENANT_ID}
 * by the {@code dev} profile — one per RBAC role.
 * <p>
 * In dev there is no real JWT, so the frontend's dev sign-in form seeds its
 * client-side principal with these ids; every request that carries a user id
 * (for example {@code GET /users/{id}/points}) therefore hits a row that the
 * seeders actually created. Keep them in sync with the mirror in the
 * frontend's {@code LoginPage.tsx}.
 * <p>
 * {@link #LEARNER} is the id all learner-scoped demo data (enrollments,
 * certificates, badges, points, notifications, interceptions) hangs off, so
 * signing in as Learner in dev lands on a populated portal.
 */
public final class DemoUsers {

    /** Demo Super Admin. */
    public static final UUID SUPER_ADMIN =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** Demo Org Admin. */
    public static final UUID ORG_ADMIN =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** Demo Manager. */
    public static final UUID MANAGER =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    /** Demo Content Editor. */
    public static final UUID CONTENT_EDITOR =
            UUID.fromString("00000000-0000-0000-0000-000000000004");

    /** Demo Analyst. */
    public static final UUID ANALYST =
            UUID.fromString("00000000-0000-0000-0000-000000000005");

    /** Demo Learner ("Minh") — the owner of all learner-scoped demo data. */
    public static final UUID LEARNER =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private DemoUsers() {
    }
}
