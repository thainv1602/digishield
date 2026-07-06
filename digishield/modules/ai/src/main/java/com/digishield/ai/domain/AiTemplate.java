package com.digishield.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A simulation-template draft — AI-generated or authored by a content editor.
 * Each template belongs to a tenant and maps onto the OpenAPI {@code SimTemplate}
 * schema. Persisted so the Content Studio library and the simulation builder can
 * browse and reuse content.
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

    /** Stable slug/reference for the rendered body (kept for back-compat). */
    @Column(name = "body_ref", nullable = false)
    private String bodyRef;

    /** The actual message body (the phishing email/SMS content). */
    @Column(name = "body", columnDefinition = "text")
    private String body;

    /** Free-text theme, e.g. "Cơ quan thuế", "Bảo hiểm xã hội" ({@code null} if unset). */
    @Column(name = "category")
    private String category;

    /** How {@link #body} is rendered: plain text or HTML (branded email). */
    @Enumerated(EnumType.STRING)
    @Column(name = "body_format", nullable = false)
    private BodyFormat bodyFormat = BodyFormat.TEXT;

    /** URL (or data URI) of the impersonated brand logo shown in the preview header. */
    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    /** JSON array of simulated attachments ({@code [{"name":..,"mime":..}]}); metadata only. */
    @Column(name = "attachments_json", columnDefinition = "text")
    private String attachmentsJson;

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
                      String bodyRef, String body, String category, Difficulty difficulty,
                      TemplateStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.channel = channel;
        this.subject = subject;
        this.bodyRef = bodyRef;
        this.body = body;
        this.category = category;
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

    public String getBody() {
        return body;
    }

    public String getCategory() {
        return category;
    }

    public BodyFormat getBodyFormat() {
        return bodyFormat;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getAttachmentsJson() {
        return attachmentsJson;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public TemplateStatus getStatus() {
        return status;
    }

    public void setBodyFormat(BodyFormat bodyFormat) {
        this.bodyFormat = bodyFormat;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public void setAttachmentsJson(String attachmentsJson) {
        this.attachmentsJson = attachmentsJson;
    }

    public void setChannel(TemplateChannel channel) {
        this.channel = channel;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public void setStatus(TemplateStatus status) {
        this.status = status;
    }
}
