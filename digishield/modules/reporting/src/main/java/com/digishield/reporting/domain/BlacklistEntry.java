package com.digishield.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A blacklist entry (domain/url/email...). Each entry belongs to a tenant.
 */
@Entity
@Table(name = "blacklist_entry")
public class BlacklistEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private BlacklistType type;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "source")
    private String source;

    protected BlacklistEntry() {
        // Required by JPA.
    }

    public BlacklistEntry(UUID id, UUID tenantId, BlacklistType type, String value, String source) {
        this.id = id;
        this.tenantId = tenantId;
        this.type = type;
        this.value = value;
        this.source = source;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public BlacklistType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getSource() {
        return source;
    }
}
