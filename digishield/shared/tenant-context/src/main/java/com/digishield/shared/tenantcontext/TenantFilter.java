package com.digishield.shared.tenantcontext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that derives the tenant id from the validated JWT and sets it into
 * {@link TenantContext} for each request.
 * <p>
 * The tenant comes from the {@code tid} claim of the JWT that the resource
 * server (see {@code SecurityConfig}) has already validated and placed in the
 * {@link SecurityContextHolder}. The tenant is therefore taken from a signed,
 * trusted token — never from a client-supplied header, which a caller could
 * forge to read another tenant's data. Requests without a {@code tid} claim
 * leave {@link TenantContext} unset, so RLS / {@code requireUuid()} fail closed.
 *
 * <p>This filter runs after Spring Security's filter chain (it is an unordered
 * {@code @Component} filter, which the servlet container places after the
 * security {@code FilterChainProxy}), so the authentication is available.
 *
 * <p>Disabled in the {@code dev} profile, where {@code DevTenantFilter} (in
 * {@code boot:app}) takes over and falls back to the fixed demo tenant so the
 * frontend works without a JWT.
 *
 * <p>Cognito must emit the {@code tid} claim (e.g. via a pre-token-generation
 * trigger or attribute mapping) for this to resolve a tenant in production.
 */
@Component
@Profile("!dev")
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TenantFilter.class);

    /** Name of the JWT claim that holds the tenant id. */
    public static final String TENANT_CLAIM = "tid";

    /**
     * Header a super admin uses to act inside another tenant. Honoured
     * <em>only</em> for callers whose validated token carries
     * {@code ROLE_SUPER_ADMIN} — see {@link #resolveTenantId}.
     */
    public static final String ACTING_TENANT_HEADER = "X-Acting-Tenant";

    /** Authority allowed to act on behalf of another tenant. */
    private static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String tenantId = resolveTenantId(request);
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.set(tenantId);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Resolves the tenant for this request.
     *
     * <p>Normally the {@code tid} claim of the validated JWT. A super admin
     * managing the platform may instead act inside another tenant by sending
     * {@link #ACTING_TENANT_HEADER}; the header is consulted only after the
     * <em>signed</em> token has been checked for {@code ROLE_SUPER_ADMIN}, so it is
     * not the forgeable {@code X-Tenant-Id} fallback that was removed earlier — an
     * ordinary caller sending it is ignored entirely.
     *
     * @return the tenant id, or {@code null} when there is no JWT or no claim
     */
    String resolveTenantId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        String own = jwt.getClaimAsString(TENANT_CLAIM);

        String acting = request.getHeader(ACTING_TENANT_HEADER);
        if (acting == null || acting.isBlank()) {
            return own;
        }
        if (!isSuperAdmin(authentication)) {
            LOG.warn("Ignoring {} from a caller without {}", ACTING_TENANT_HEADER, SUPER_ADMIN);
            return own;
        }
        // Parse rather than merely validate: everything downstream — the log line
        // below, the RLS GUC — then carries the canonical form UUID rebuilt from
        // the parsed fields, never the caller's own bytes. That rules out log
        // injection (no newlines can survive) as well as junk reaching Postgres.
        UUID parsed = parseUuid(acting.trim());
        if (parsed == null) {
            LOG.warn("Ignoring malformed {} header", ACTING_TENANT_HEADER);
            return own;
        }
        String canonical = parsed.toString();
        if (!canonical.equals(own)) {
            // Every cross-tenant request is visible in the logs, not just the
            // moment the console entered the tenant.
            LOG.info("Super admin acting inside tenant {}", parsed);
        }
        return canonical;
    }

    private static boolean isSuperAdmin(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (SUPER_ADMIN.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** Parsed UUID, or {@code null} when {@code value} is not one. */
    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
