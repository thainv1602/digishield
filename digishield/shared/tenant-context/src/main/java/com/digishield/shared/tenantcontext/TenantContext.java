package com.digishield.shared.tenantcontext;

import java.util.UUID;

/**
 * Stores the tenant id of the current request on a per-thread basis.
 * <p>
 * The raw value is the tenant identifier string taken from the JWT {@code tid} claim
 * (kept as String for the RLS GUC set by the persistence aspect). Domain code should use
 * {@link #requireUuid()} so that {@code tenant_id} is handled as a {@link UUID} everywhere.
 * <p>
 * Note: a plain {@link RuntimeException} is used to avoid depending on Spring Security
 * at this layer (keeping the module independent).
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * Sets the tenant id for the current thread.
     */
    public static void set(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Returns the tenant id, may return {@code null} if it has not been set.
     */
    public static String get() {
        return CURRENT_TENANT.get();
    }

    /**
     * Returns the tenant id and throws an exception if absent (a tenant is required).
     *
     * @throws IllegalStateException if the tenant id has not been set
     */
    public static String require() {
        String tenantId = CURRENT_TENANT.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Tenant context has not been set for the current request");
        }
        return tenantId;
    }

    /**
     * Returns the current tenant id as a {@link UUID}; this is the form domain code,
     * repositories and entities should use ({@code tenant_id} is a {@code uuid} column).
     *
     * @throws IllegalStateException if the tenant id has not been set
     * @throws IllegalArgumentException if the tenant id is not a valid UUID
     */
    public static UUID requireUuid() {
        return UUID.fromString(require());
    }

    /**
     * Removes the tenant id from the current thread (always call within a finally block).
     */
    /**
     * Restores whatever was set before a scoped override, clearing when there
     * was nothing.
     *
     * <p>Listeners and jobs must restore rather than clear. On their own thread
     * -- which is where {@code @ApplicationModuleListener} puts them while
     * {@code @Async} is active -- clearing is right. But the same code runs
     * inline on the caller's thread when it is not, and clearing there wipes a
     * tenant the caller still needs: a campaign send lost its tenant halfway
     * through exactly this way.
     *
     * @param previous the value captured before the override, may be null
     */
    public static void restore(String previous) {
        if (previous == null) {
            clear();
        } else {
            set(previous);
        }
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
