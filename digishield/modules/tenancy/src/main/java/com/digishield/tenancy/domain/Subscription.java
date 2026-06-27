package com.digishield.tenancy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A tenant's current subscription to a {@link Plan}.
 */
@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    /** Subscription status: trial | active | past_due | canceled. */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "renews_at")
    private LocalDate renewsAt;

    /** Default constructor required by JPA. */
    protected Subscription() {
    }

    public Subscription(UUID id, UUID tenantId, UUID planId, String status, LocalDate renewsAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.planId = planId;
        this.status = status;
        this.renewsAt = renewsAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getRenewsAt() {
        return renewsAt;
    }

    public void setPlanId(UUID planId) {
        this.planId = planId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setRenewsAt(LocalDate renewsAt) {
        this.renewsAt = renewsAt;
    }
}
