package com.digishield.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Per-user gamification standing within a tenant: accumulated points and the
 * display name used by the leaderboard.
 */
@Entity
@Table(name = "gamification_profile")
public class GamificationProfile {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "department")
    private String department;

    @Column(name = "points", nullable = false)
    private int points;

    /** Default constructor required by JPA. */
    protected GamificationProfile() {
    }

    public GamificationProfile(UUID id, UUID tenantId, UUID userId, String displayName,
                               String department, int points) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.displayName = displayName;
        this.department = department;
        this.points = points;
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

    public String getDisplayName() {
        return displayName;
    }

    public String getDepartment() {
        return department;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }
}
