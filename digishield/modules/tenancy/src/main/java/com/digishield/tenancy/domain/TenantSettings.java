package com.digishield.tenancy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Tenant configuration (branding, policy and default locale) for the Org
 * Settings screen. One row per tenant.
 * <p>
 * {@code brandingJson} (logo, colors, subdomain) and {@code policyJson} (risk
 * thresholds, mandatory training) hold JSON objects as text.
 */
@Entity
@Table(name = "tenant_settings")
public class TenantSettings {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branding_json", length = 4000)
    private String brandingJson;

    @Column(name = "policy_json", length = 4000)
    private String policyJson;

    @Column(name = "default_locale")
    private String defaultLocale;

    /** Default constructor required by JPA. */
    protected TenantSettings() {
    }

    public TenantSettings(UUID id, UUID tenantId, String brandingJson, String policyJson,
                          String defaultLocale) {
        this.id = id;
        this.tenantId = tenantId;
        this.brandingJson = brandingJson;
        this.policyJson = policyJson;
        this.defaultLocale = defaultLocale;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getBrandingJson() {
        return brandingJson;
    }

    public String getPolicyJson() {
        return policyJson;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setBrandingJson(String brandingJson) {
        this.brandingJson = brandingJson;
    }

    public void setPolicyJson(String policyJson) {
        this.policyJson = policyJson;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }
}
