package com.digishield.shared.tenantcontext;

import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Per-thread marker for a <em>platform-scoped</em> read: a query that is about the
 * platform itself (the tenant registry, subscriptions, usage metering) rather than
 * about one tenant's data, and therefore must not be constrained by Row-Level
 * Security.
 *
 * <p>Without this, the Super Admin console cannot work at all: the {@code tenant}
 * table carries its own {@code tenant_isolation} policy, so a super admin scoped to
 * their own {@code tid} can only ever read the single row for their own tenant.
 *
 * <p>While the scope is active, {@code RlsTenantAspect} skips the
 * {@code app.tenant_id} GUC and the {@code SET LOCAL ROLE}, leaving the query with
 * the connection's own (RLS-bypassing) privileges — the same escape hatch
 * {@link SeedingContext} gives the demo seeders.
 *
 * <p><strong>Safe by construction:</strong> {@link #call(Supplier)} refuses to open
 * the scope unless the current, already-validated authentication carries
 * {@code ROLE_SUPER_ADMIN}. Callers cannot widen their own privileges by wrapping a
 * query in it, and the scope always closes in a {@code finally} block, so a leaked
 * flag cannot disable RLS for later work on the same thread.
 *
 * <p><strong>Transaction-wide effect.</strong> The aspect applies {@code SET LOCAL
 * ROLE} / {@code set_config(..., true)} per <em>transaction</em>, so skipping them
 * for the first repository call leaves the whole surrounding transaction running
 * with the connection's privileges — including a flush at commit. That is what
 * makes {@code createTenant} (inserting a row whose {@code tenant_id} is not the
 * caller's) possible at all, but it means a platform-scoped call must stay in a
 * short, {@code SUPER_ADMIN}-only transaction and must not be mixed with
 * tenant-scoped writes.
 */
public final class PlatformScope {

    /** Authority required to read across tenants. */
    private static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private PlatformScope() {
    }

    /** True while a platform-scoped read is in progress on this thread. */
    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    /**
     * Whether the current caller may open the scope. Lets a method that serves
     * both a super admin (cross-tenant) and a tenant admin (own tenant only) pick
     * the right path instead of failing for the latter.
     */
    public static boolean isAvailable() {
        return isSuperAdmin();
    }

    /**
     * Runs {@code action} outside tenant isolation.
     *
     * @throws IllegalStateException when the caller is not an authenticated
     *                               {@code SUPER_ADMIN} — a platform-scoped read is
     *                               never valid for anyone else
     */
    public static <T> T call(Supplier<T> action) {
        if (!isSuperAdmin()) {
            throw new IllegalStateException(
                    "A platform-scoped read requires ROLE_SUPER_ADMIN");
        }
        ACTIVE.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            ACTIVE.remove();
        }
    }

    private static boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (SUPER_ADMIN.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
