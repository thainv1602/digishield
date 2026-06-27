package com.digishield.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Aggregated risk score for a named department. Each row belongs to a tenant.
 * <p>
 * Backs the "high-risk departments" section of the admin dashboard, where a
 * human-readable department name is required alongside the score.
 */
@Entity
@Table(name = "department_risk")
public class DepartmentRisk {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "phish_prone_pct")
    private double phishPronePct;

    @Column(name = "headcount")
    private int headcount;

    protected DepartmentRisk() {
        // Required by JPA.
    }

    public DepartmentRisk(UUID id, UUID tenantId, String name, int riskScore,
                          double phishPronePct, int headcount) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.riskScore = riskScore;
        this.phishPronePct = phishPronePct;
        this.headcount = headcount;
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

    public int getRiskScore() {
        return riskScore;
    }

    public double getPhishPronePct() {
        return phishPronePct;
    }

    public int getHeadcount() {
        return headcount;
    }
}
