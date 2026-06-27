package com.digishield.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * An AI-generated simulation template draft. Each template belongs to a tenant
 * and maps onto the OpenAPI {@code SimTemplate} schema. Drafts are persisted so
 * the simulation builder can browse previously generated content.
 */
@Entity
@Table(name = "ai_template")
public class AiTemplate {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private TemplateChannel channel;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body_ref", nullable = false)
    private String bodyRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TemplateStatus status;

    protected AiTemplate() {
        // Required by JPA.
    }

    public AiTemplate(UUID id, UUID tenantId, TemplateChannel channel, String subject,
                      String bodyRef, Difficulty difficulty, TemplateStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.channel = channel;
        this.subject = subject;
        this.bodyRef = bodyRef;
        this.difficulty = difficulty;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public TemplateChannel getChannel() {
        return channel;
    }

    public String getSubject() {
        return subject;
    }

    public String getBodyRef() {
        return bodyRef;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public TemplateStatus getStatus() {
        return status;
    }
}
