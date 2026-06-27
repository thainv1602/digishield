package com.digishield.tenancy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Feature flag belonging to a specific tenant.
 */
@Entity
@Table(name = "feature_flag")
public class FeatureFlag {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "flag_key", nullable = false)
    private String key;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Default constructor required by JPA. */
    protected FeatureFlag() {
    }

    public FeatureFlag(UUID id, UUID tenantId, String key, boolean enabled) {
        this.id = id;
        this.tenantId = tenantId;
        this.key = key;
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getKey() {
        return key;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
