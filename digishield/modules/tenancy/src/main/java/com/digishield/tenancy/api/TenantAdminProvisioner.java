package com.digishield.tenancy.api;

import java.util.UUID;

/**
 * Creates the first administrator of a newly created tenant.
 *
 * <p>Implemented outside this module: creating a user means creating a sign-in
 * account, which is the auth module's business, and tenancy is not allowed to
 * depend on it. The application shell wires the two together, the same way it
 * supplies the audit recorder and the notification gateways.
 *
 * <p>Called inside the transaction that creates the tenant, so a failure here
 * takes the tenant with it. That is the point: an organisation nobody can log
 * into is worse than no organisation, because it looks finished.
 */
public interface TenantAdminProvisioner {

    /**
     * Creates an {@code org_admin} for a tenant that has just been created.
     *
     * @param tenantId the new tenant
     * @param email    the person who will administer it; they receive the
     *                 provider's invitation, which is how they first sign in
     */
    void provisionAdmin(UUID tenantId, String email);
}
