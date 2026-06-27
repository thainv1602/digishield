package com.digishield.tenancy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * SCIM / SSO identity-provider configuration for a tenant (Super Admin SCIM
 * &amp; SSO Config screen).
 */
@Entity
@Table(name = "scim_config")
public class ScimConfig {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Connected IdP display name, e.g. "Microsoft Entra ID (Azure AD)". */
    @Column(name = "idp_name", nullable = false)
    private String idpName;

    @Column(name = "connected", nullable = false)
    private boolean connected;

    @Column(name = "idp_tenant_id")
    private String idpTenantId;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "scim_endpoint")
    private String scimEndpoint;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "synced_user_count")
    private Integer syncedUserCount;

    @Column(name = "sync_error_count")
    private Integer syncErrorCount;

    /** Default constructor required by JPA. */
    protected ScimConfig() {
    }

    public ScimConfig(UUID id, UUID tenantId, String idpName, boolean connected,
                      String idpTenantId, String clientId, String scimEndpoint,
                      Instant lastSyncAt, Integer syncedUserCount, Integer syncErrorCount) {
        this.id = id;
        this.tenantId = tenantId;
        this.idpName = idpName;
        this.connected = connected;
        this.idpTenantId = idpTenantId;
        this.clientId = clientId;
        this.scimEndpoint = scimEndpoint;
        this.lastSyncAt = lastSyncAt;
        this.syncedUserCount = syncedUserCount;
        this.syncErrorCount = syncErrorCount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getIdpName() {
        return idpName;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getIdpTenantId() {
        return idpTenantId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getScimEndpoint() {
        return scimEndpoint;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public Integer getSyncedUserCount() {
        return syncedUserCount;
    }

    public Integer getSyncErrorCount() {
        return syncErrorCount;
    }
}
