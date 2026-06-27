package com.digishield.simulation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Phishing simulation campaign. Each campaign belongs to a tenant.
 */
@Entity
@Table(name = "sim_campaign")
public class SimCampaign {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CampaignStatus status;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "name")
    private String name;

    protected SimCampaign() {
        // Required by JPA.
    }

    public SimCampaign(UUID id, UUID tenantId, Channel channel, CampaignStatus status, UUID templateId) {
        this(id, tenantId, channel, status, templateId, null);
    }

    public SimCampaign(UUID id, UUID tenantId, Channel channel, CampaignStatus status,
                       UUID templateId, String name) {
        this.id = id;
        this.tenantId = tenantId;
        this.channel = channel;
        this.status = status;
        this.templateId = templateId;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Channel getChannel() {
        return channel;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public String getName() {
        return name;
    }
}
