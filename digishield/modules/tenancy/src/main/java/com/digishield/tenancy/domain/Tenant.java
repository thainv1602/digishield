package com.digishield.tenancy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Tenant (organization customer) of the DigiShield platform.
 * <p>
 * The {@code tenantId} field (business identifier string) is stored alongside for
 * consistency with the multi-tenant model of other modules; for the tenant table
 * itself, this value usually matches the string representation of {@code id}.
 */
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false)
    private TenantTier tier;

    @Column(name = "data_region", nullable = false)
    private String dataRegion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TenantStatus status;

    /** Number of users in the tenant (shown in the Super Tenant Console). */
    @Column(name = "user_count")
    private Integer userCount;

    /** Primary domain of the tenant (e.g. abc.gov.vn). */
    @Column(name = "domain")
    private String domain;

    /** Default constructor required by JPA. */
    protected Tenant() {
    }

    public Tenant(UUID id, UUID tenantId, String name, TenantTier tier,
                  String dataRegion, TenantStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.tier = tier;
        this.dataRegion = dataRegion;
        this.status = status;
    }

    public Tenant(UUID id, UUID tenantId, String name, TenantTier tier,
                  String dataRegion, TenantStatus status, Integer userCount, String domain) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.tier = tier;
        this.dataRegion = dataRegion;
        this.status = status;
        this.userCount = userCount;
        this.domain = domain;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public TenantTier getTier() {
        return tier;
    }

    public String getDataRegion() {
        return dataRegion;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public Integer getUserCount() {
        return userCount;
    }

    public String getDomain() {
        return domain;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTier(TenantTier tier) {
        this.tier = tier;
    }

    public void setDataRegion(String dataRegion) {
        this.dataRegion = dataRegion;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }
}
