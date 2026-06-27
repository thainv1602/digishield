package com.digishield.simulation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Per-user outcome of a simulation campaign, used to render the campaign
 * results table (user, department, final action, learning status).
 */
@Entity
@Table(name = "sim_result")
public class SimResult {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(name = "department")
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private SimAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_status", nullable = false)
    private LearningStatus learningStatus;

    protected SimResult() {
        // Required by JPA.
    }

    public SimResult(UUID id, UUID tenantId, UUID campaignId, UUID userId, String userName,
                     String department, SimAction action, LearningStatus learningStatus) {
        this.id = id;
        this.tenantId = tenantId;
        this.campaignId = campaignId;
        this.userId = userId;
        this.userName = userName;
        this.department = department;
        this.action = action;
        this.learningStatus = learningStatus;
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

    public String getUserName() {
        return userName;
    }

    public String getDepartment() {
        return department;
    }

    public SimAction getAction() {
        return action;
    }

    public LearningStatus getLearningStatus() {
        return learningStatus;
    }
}
