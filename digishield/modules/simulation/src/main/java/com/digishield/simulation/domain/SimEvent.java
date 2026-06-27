package com.digishield.simulation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A user interaction event within a simulation campaign.
 */
@Entity
@Table(name = "sim_event")
public class SimEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private SimAction action;

    @Column(name = "ts", nullable = false)
    private Instant ts;

    protected SimEvent() {
        // Required by JPA.
    }

    public SimEvent(UUID id, UUID tenantId, UUID campaignId, UUID userId, SimAction action, Instant ts) {
        this.id = id;
        this.tenantId = tenantId;
        this.campaignId = campaignId;
        this.userId = userId;
        this.action = action;
        this.ts = ts;
    }

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

    public SimAction getAction() {
        return action;
    }

    public Instant getTs() {
        return ts;
    }
}
