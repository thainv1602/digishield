package com.digishield.tenancy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An audit-log entry for a sensitive action (Super Admin Audit Log screen).
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ts", nullable = false)
    private Instant ts;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "target")
    private String target;

    @Column(name = "ip")
    private String ip;

    /** Severity used for color-coding: critical|sensitive|standard. */
    @Column(name = "severity", nullable = false)
    private String severity;

    /** Default constructor required by JPA. */
    protected AuditLog() {
    }

    public AuditLog(UUID id, UUID tenantId, Instant ts, String actor, String action,
                    String target, String ip, String severity) {
        this.id = id;
        this.tenantId = tenantId;
        this.ts = ts;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.ip = ip;
        this.severity = severity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Instant getTs() {
        return ts;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getIp() {
        return ip;
    }

    public String getSeverity() {
        return severity;
    }
}
