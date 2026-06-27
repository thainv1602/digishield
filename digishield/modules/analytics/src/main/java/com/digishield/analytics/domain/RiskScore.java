package com.digishield.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Risk score computed for a specific scope. Each record belongs to a tenant.
 */
@Entity
@Table(name = "risk_score")
public class RiskScore {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private RiskScope scope;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Column(name = "value", nullable = false)
    private int value;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected RiskScore() {
        // Required by JPA.
    }

    public RiskScore(UUID id, UUID tenantId, RiskScope scope, UUID scopeId, int value, Instant computedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.scope = scope;
        this.scopeId = scopeId;
        this.value = value;
        this.computedAt = computedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public RiskScope getScope() {
        return scope;
    }

    public UUID getScopeId() {
        return scopeId;
    }

    public int getValue() {
        return value;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
