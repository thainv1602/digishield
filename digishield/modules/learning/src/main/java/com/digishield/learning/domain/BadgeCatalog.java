package com.digishield.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A tenant's badge definition (the catalog of badges it offers), as opposed to
 * {@link Badge} which is a per-user badge instance.
 */
@Entity
@Table(name = "badge_catalog")
public class BadgeCatalog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    /** Icon hint consumed by the frontend (e.g. "shield", "target", "zap"). */
    @Column(name = "icon_ref")
    private String iconRef;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** Default constructor required by JPA. */
    protected BadgeCatalog() {
    }

    public BadgeCatalog(UUID id, UUID tenantId, String name, String description, String iconRef) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.iconRef = iconRef;
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

    public String getDescription() {
        return description;
    }

    public String getIconRef() {
        return iconRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setIconRef(String iconRef) {
        this.iconRef = iconRef;
    }
}
