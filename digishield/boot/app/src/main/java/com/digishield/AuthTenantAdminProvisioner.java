package com.digishield;

import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.UserUpsert;
import com.digishield.shared.tenantcontext.TenantContext;
import com.digishield.tenancy.api.TenantAdminProvisioner;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Creates a new tenant's first administrator by going through the auth module.
 *
 * <p>Lives in the application shell because it joins two modules that are not
 * allowed to see each other: tenancy declares the SPI, auth owns users, and
 * neither depends on the other.
 *
 * <p>The tenant context is swapped for the duration of the call and put back
 * afterwards. {@code createUser} reads the context to decide which organisation
 * the account belongs to, and the caller here is a super admin sitting in their
 * own tenant -- without the swap, the first administrator of a new organisation
 * would be created inside the old one.
 */
@Component
class AuthTenantAdminProvisioner implements TenantAdminProvisioner {

    private static final Logger LOG = LoggerFactory.getLogger(AuthTenantAdminProvisioner.class);

    private final AuthService authService;

    AuthTenantAdminProvisioner(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void provisionAdmin(UUID tenantId, String email) {
        String previous = TenantContext.get();
        TenantContext.set(tenantId.toString());
        try {
            authService.createUser(new UserUpsert(email, "org_admin", null, null));
            LOG.info("Created the first administrator of tenant {}", tenantId);
        } finally {
            // Restored rather than cleared: this runs inside the super admin's
            // own request, which still has work to do in their tenant.
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }
}
