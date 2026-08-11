package com.digishield.simulation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

/**
 * A single recipient of a launched simulation campaign. Its {@code id} doubles
 * as the opaque tracking token embedded in the recipient's simulation link.
 */
@Entity
@Table(name = "sim_recipient")
public class SimRecipient implements Persistable<UUID> {

    /**
     * Ids are assigned by the caller, so Spring Data would treat every
     * instance as detached and call merge() -- a SELECT before each INSERT.
     * Saying so explicitly keeps inserts on the persist() path, which is
     * also the only path Hibernate will batch.
     */
    @Transient
    private boolean newEntity = true;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;

    @Column(name = "clicked_at")
    private Instant clickedAt;

    protected SimRecipient() {
        // Required by JPA.
    }

    public SimRecipient(UUID id, UUID tenantId, UUID campaignId, UUID userId, Instant deliveredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.campaignId = campaignId;
        this.userId = userId;
        this.deliveredAt = deliveredAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(Instant clickedAt) {
        this.clickedAt = clickedAt;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }
}
