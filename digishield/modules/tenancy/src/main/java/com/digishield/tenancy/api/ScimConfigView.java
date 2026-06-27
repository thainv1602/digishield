package com.digishield.tenancy.api;

import java.time.Instant;
import java.util.UUID;

/**
 * SCIM / SSO configuration view (SCIM &amp; SSO Config screen).
 *
 * @param tenantId        DigiShield tenant id
 * @param idpName         connected IdP display name
 * @param connected       whether the IdP is connected
 * @param idpTenantId     IdP-side tenant id (e.g. Azure tenant id)
 * @param clientId        OAuth client id
 * @param scimEndpoint    SCIM endpoint URL
 * @param lastSyncAt      last successful sync timestamp
 * @param syncedUserCount number of users synced
 * @param syncErrorCount  number of sync errors
 * @param syncStatus      derived status: ok|error|never
 */
public record ScimConfigView(UUID tenantId, String idpName, boolean connected,
                             String idpTenantId, String clientId, String scimEndpoint,
                             Instant lastSyncAt, Integer syncedUserCount,
                             Integer syncErrorCount, String syncStatus) {
}
