package com.digishield.interception.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity: records a transaction intervention for a tenant.
 */
@Entity
@Table(name = "intervention_event")
public class InterventionEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Tenant that owns the record (multi-tenant). */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** List of detected signals, stored as a string (e.g. comma-separated). */
    @Column(name = "signals", length = 1000)
    private String signals;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 32)
    private Decision decision;

    @Column(name = "ts", nullable = false)
    private Instant ts;

    /** Default constructor required by JPA. */
    protected InterventionEvent() {
    }

    public InterventionEvent(UUID id,
                            UUID tenantId,
                            UUID userId,
                            String signals,
                            Decision decision,
                            Instant ts) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.signals = signals;
        this.decision = decision;
        this.ts = ts;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSignals() {
        return signals;
    }

    public Decision getDecision() {
        return decision;
    }

    public Instant getTs() {
        return ts;
    }
}
