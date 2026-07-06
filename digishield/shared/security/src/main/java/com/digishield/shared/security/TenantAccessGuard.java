package com.digishield.shared.security;

import com.digishield.shared.tenantcontext.TenantContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * SpEL helper for the hybrid tenancy authorization: it answers whether a
 * path-supplied tenant id is the caller's <em>own</em> tenant (the signed
 * {@code tid} claim, exposed via {@link TenantContext}). Referenced from
 * {@code @PreAuthorize} as {@code @tenantGuard.isSelf(#tenantId)} so a tenant
 * admin can only manage their own tenant while a platform {@code SUPER_ADMIN} is
 * unrestricted.
 */
@Component("tenantGuard")
public class TenantAccessGuard {

    /**
     * @return {@code true} iff {@code tenantId} equals the caller's current tenant.
     */
    public boolean isSelf(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        String current = TenantContext.get();
        return current != null && current.equals(tenantId.toString());
    }
}
