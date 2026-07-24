package com.digishield.shared.persistence;

/**
 * Per-thread flag marking that demo seeding is in progress.
 * <p>
 * Seeders create data across multiple tenants and run at startup with no request
 * tenant context, so they must run as the connected superuser (which bypasses
 * RLS). While this flag is set, {@link RlsTenantAspect} skips setting the
 * {@code app.tenant_id} GUC and the {@code SET LOCAL ROLE}, so seeder inserts are
 * not constrained by the tenant_isolation policies. Cleared as soon as seeding
 * finishes, restoring normal per-request RLS enforcement.
 */
public final class SeedingContext {

    private static final ThreadLocal<Boolean> SEEDING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private SeedingContext() {
    }

    public static boolean isSeeding() {
        return Boolean.TRUE.equals(SEEDING.get());
    }

    public static void begin() {
        SEEDING.set(Boolean.TRUE);
    }

    public static void end() {
        SEEDING.remove();
    }
}
