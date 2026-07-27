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

    /**
     * Measured share (%) of this scope's people who clicked a simulation in the
     * scoring window. Null on rows written before the rollup job existed, and on
     * any score recorded from a single user's signals — a share needs a group.
     */
    @Column(name = "phish_prone_pct")
    private Double phishPronePct;

    protected RiskScore() {
        // Required by JPA.
    }

    /** A score with no measured rate — a single user's, or a pre-rollup row. */
    public RiskScore(UUID id, UUID tenantId, RiskScope scope, UUID scopeId, int value, Instant computedAt) {
        this(id, tenantId, scope, scopeId, value, computedAt, null);
    }

    public RiskScore(UUID id, UUID tenantId, RiskScope scope, UUID scopeId, int value,
                     Instant computedAt, Double phishPronePct) {
        this.id = id;
        this.tenantId = tenantId;
        this.scope = scope;
        this.scopeId = scopeId;
        this.value = value;
        this.computedAt = computedAt;
        this.phishPronePct = phishPronePct;
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

    public Double getPhishPronePct() {
        return phishPronePct;
    }

    public int getValue() {
        return value;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
