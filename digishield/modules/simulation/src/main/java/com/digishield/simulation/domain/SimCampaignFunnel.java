package com.digishield.simulation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Materialized funnel aggregate for a simulation campaign (delivered, open,
 * click, submit, report counts). Keyed by campaign id.
 * <p>
 * Using a stored aggregate avoids scanning the (potentially large) raw event
 * stream on every read of the campaign results screen.
 */
@Entity
@Table(name = "sim_campaign_funnel")
public class SimCampaignFunnel {

    @Id
    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "delivered", nullable = false)
    private long delivered;

    @Column(name = "opened", nullable = false)
    private long opened;

    @Column(name = "clicked", nullable = false)
    private long clicked;

    @Column(name = "submitted", nullable = false)
    private long submitted;

    @Column(name = "reported", nullable = false)
    private long reported;

    protected SimCampaignFunnel() {
        // Required by JPA.
    }

    public SimCampaignFunnel(UUID campaignId, UUID tenantId, long delivered, long opened,
                             long clicked, long submitted, long reported) {
        this.campaignId = campaignId;
        this.tenantId = tenantId;
        this.delivered = delivered;
        this.opened = opened;
        this.clicked = clicked;
        this.submitted = submitted;
        this.reported = reported;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public long getDelivered() {
        return delivered;
    }

    public long getOpened() {
        return opened;
    }

    public long getClicked() {
        return clicked;
    }

    public long getSubmitted() {
        return submitted;
    }

    public long getReported() {
        return reported;
    }
}
