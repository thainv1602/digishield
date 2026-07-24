package com.digishield.tenancy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A user's membership in a {@link Group}. One row per (group, user); the unique
 * constraint on {@code (group_id, user_id)} prevents duplicates.
 */
@Entity
@Table(name = "group_member")
public class GroupMember {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Set by the database default ({@code now()}); read-only from JPA. */
    @Column(name = "added_at", insertable = false, updatable = false)
    private OffsetDateTime addedAt;

    /** Default constructor required by JPA. */
    protected GroupMember() {
    }

    public GroupMember(UUID id, UUID tenantId, UUID groupId, UUID userId) {
        this.id = id;
        this.tenantId = tenantId;
        this.groupId = groupId;
        this.userId = userId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public OffsetDateTime getAddedAt() {
        return addedAt;
    }
}
