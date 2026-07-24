package com.digishield.shared.persistence;

import com.digishield.shared.tenantcontext.TenantContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Aspect that enforces PostgreSQL Row-Level Security (RLS).
 * <p>
 * Before each {@code @Transactional} method, it sets the session variable
 * {@code app.tenant_id} via {@code SET LOCAL} so that RLS policies filter data
 * by the current tenant. {@code SET LOCAL} only takes effect within the current
 * transaction, so it must run inside an open transaction.
 *
 * <p>The app connects to PostgreSQL as the owner/superuser {@code digishield} so
 * that Flyway and the demo seeders can run, but PostgreSQL <em>bypasses RLS for
 * superusers</em>. To get real isolation, this aspect also issues
 * {@code SET LOCAL ROLE <appRole>} (a NOSUPERUSER / NOBYPASSRLS role created by
 * migration {@code V2026.07.24.001}); within the transaction the effective role
 * is non-privileged, so the {@code tenant_isolation} policies apply. Configurable
 * via {@code digishield.rls.app-role} — set it blank to disable the SET ROLE
 * (e.g. when the app already connects as a non-superuser). Flyway and the seeders
 * do not go through this aspect's SET ROLE at the connection level, so they keep
 * their privileges.
 *
 * <p>Disabled in the {@code dev} profile: it issues PostgreSQL-only
 * {@code set_config} calls that the in-memory H2 database does not support.
 */
@Aspect
@Component
@Profile("!dev")
public class RlsTenantAspect
        implements org.springframework.context.ApplicationListener<org.springframework.context.event.ContextRefreshedEvent> {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RlsTenantAspect.class);
    /** Matches [a-z_][a-z0-9_]* — guards the un-parameterisable SET ROLE identifier. */
    private static final java.util.regex.Pattern SAFE_ROLE = java.util.regex.Pattern.compile("[a-z_][a-z0-9_]*");
    private static volatile boolean contextRefreshed = false;
    private final JdbcTemplate jdbcTemplate;
    private final String appRole;

    public RlsTenantAspect(JdbcTemplate jdbcTemplate,
                           @Value("${digishield.rls.app-role:digishield_app}") String appRole) {
        this.jdbcTemplate = jdbcTemplate;
        String role = appRole == null ? "" : appRole.trim();
        if (!role.isEmpty() && !SAFE_ROLE.matcher(role).matches()) {
            throw new IllegalArgumentException("Invalid digishield.rls.app-role: " + appRole);
        }
        this.appRole = role;
    }

    @Override
    public void onApplicationEvent(org.springframework.context.event.ContextRefreshedEvent event) {
        contextRefreshed = true;
    }

    /**
     * Applies to all Spring Data repository calls.
     */
    @Before("this(org.springframework.data.repository.Repository)")
    public void setTenantForTransaction() {
        // Demo seeders create cross-tenant data as the superuser — leave RLS
        // bypassed (no GUC, no SET ROLE) while seeding is in progress.
        if (SeedingContext.isSeeding()) {
            return;
        }
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            if (contextRefreshed) {
                throw new IllegalStateException("Tenant context has not been set for the current request");
            }
            return;
        }
        LOGGER.debug("Setting tenant_id GUC to: {}", tenantId);
        // Use set_config to avoid parameterization issues with SET LOCAL.
        // The 3rd parameter = true => local scope (only within the current transaction).
        jdbcTemplate.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId);
        // Drop superuser privileges for the rest of this transaction so RLS is
        // actually enforced. Role name is validated in the constructor (SET ROLE
        // cannot be parameterised). Blank -> skip (app already non-superuser).
        if (!appRole.isEmpty()) {
            jdbcTemplate.execute("SET LOCAL ROLE " + appRole);
        }
    }
}
