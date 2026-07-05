package com.digishield.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A record of one AIDA orchestration run (risk recompute + auto-enroll) for a
 * tenant, so the AIDA console can show recent-run history. Each record belongs
 * to a tenant.
 */
@Entity
@Table(name = "aida_run")
public class AidaRun {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Target scope of the run, e.g. {@code "org"} or a department key. */
    @Column(name = "scope", nullable = false)
    private String scope;

    /** Optional id the scope refers to (e.g. a department/user), may be null. */
    @Column(name = "scope_id")
    private UUID scopeId;

    /** Outcome of the run, e.g. {@code "success"}. */
    @Column(name = "status", nullable = false)
    private String status;

    /** Human-readable summary of what the run did. */
    @Column(name = "summary")
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AidaRun() {
        // Required by JPA.
    }

    public AidaRun(UUID id, UUID tenantId, String scope, UUID scopeId,
                   String status, String summary, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.scope = scope;
        this.scopeId = scopeId;
        this.status = status;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    /**
     * Finalises the run once the orchestration completes, recording its outcome
     * status and human-readable summary.
     */
    public void complete(String status, String summary) {
        this.status = status;
        this.summary = summary;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getScope() {
        return scope;
    }

    public UUID getScopeId() {
        return scopeId;
    }

    public String getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
