package com.digishield.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A gamification badge awarded to (or available for) a user within a tenant.
 */
@Entity
@Table(name = "badge")
public class Badge {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    /** Icon hint consumed by the frontend (e.g. "shield", "target", "zap"). */
    @Column(name = "icon_ref")
    private String iconRef;

    @Column(name = "earned", nullable = false)
    private boolean earned;

    @Column(name = "awarded_at")
    private Instant awardedAt;

    /** Default constructor required by JPA. */
    protected Badge() {
    }

    public Badge(UUID id, UUID tenantId, UUID userId, String name, String description,
                 String iconRef, boolean earned, Instant awardedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.iconRef = iconRef;
        this.earned = earned;
        this.awardedAt = awardedAt;
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

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIconRef() {
        return iconRef;
    }

    public boolean isEarned() {
        return earned;
    }

    public Instant getAwardedAt() {
        return awardedAt;
    }
}
