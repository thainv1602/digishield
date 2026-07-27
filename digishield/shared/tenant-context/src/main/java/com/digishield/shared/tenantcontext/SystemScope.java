package com.digishield.shared.tenantcontext;

import java.util.function.Supplier;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Per-thread marker for background work that belongs to no tenant — a scheduled
 * job deciding <em>which</em> tenants it must process.
 *
 * <p>Scheduled work has no request and therefore no tenant, so
 * {@code RlsTenantAspect} would fail it closed. This opens exactly the gap
 * needed to enumerate tenants; everything after that runs with
 * {@link TenantContext} set to one tenant at a time, under normal isolation. The
 * bypass covers a single query rather than a whole job, so a mistake in the
 * aggregation cannot reach across tenants.
 *
 * <p>Unlike {@link PlatformScope} there is no principal to authorise, because
 * nobody is logged in. The guard is different in kind: this refuses to open on a
 * thread that is serving an HTTP request. A request has a tenant and a caller,
 * and must never be able to reach for tenant-free access — so if this is ever
 * called from a controller, deliberately or by a stray refactor, it throws
 * rather than quietly widening what that request can see.
 */
public final class SystemScope {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private SystemScope() {
    }

    /** True while tenant-free background work is in progress on this thread. */
    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    /**
     * Runs {@code action} outside tenant isolation.
     *
     * @throws IllegalStateException when called while handling an HTTP request
     */
    public static <T> T call(Supplier<T> action) {
        if (RequestContextHolder.getRequestAttributes() != null) {
            throw new IllegalStateException(
                    "SystemScope is for background work; a request-handling thread has a tenant");
        }
        ACTIVE.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            ACTIVE.remove();
        }
    }
}
