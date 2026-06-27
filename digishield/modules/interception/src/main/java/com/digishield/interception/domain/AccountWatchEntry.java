package com.digishield.interception.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA entity: an entry in a tenant's watchlist.
 */
@Entity
@Table(name = "account_watch_entry")
public class AccountWatchEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Tenant that owns the record (multi-tenant). */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private WatchType type;

    @Column(name = "value", nullable = false, length = 128)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 32)
    private RiskLevel riskLevel;

    @Column(name = "source", length = 128)
    private String source;

    /** Default constructor required by JPA. */
    protected AccountWatchEntry() {
    }

    public AccountWatchEntry(UUID id,
                            UUID tenantId,
                            WatchType type,
                            String value,
                            RiskLevel riskLevel,
                            String source) {
        this.id = id;
        this.tenantId = tenantId;
        this.type = type;
        this.value = value;
        this.riskLevel = riskLevel;
        this.source = source;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public WatchType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getSource() {
        return source;
    }
}
