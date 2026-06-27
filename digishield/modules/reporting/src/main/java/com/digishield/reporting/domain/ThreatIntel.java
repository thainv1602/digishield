package com.digishield.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A raw threat-intelligence record ingested from an external feed (e.g. NCSC).
 * Once triaged it can be converted into de-identified training content; when
 * that happens {@link #convertedTemplateId} points at the generated template.
 * Each record belongs to a tenant.
 */
@Entity
@Table(name = "threat_intel")
public class ThreatIntel {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Origin of the intel (e.g. "NCSC", "A05", "feed"). */
    @Column(name = "source")
    private String source;

    /** Raw payload of the threat sample. */
    @Lob
    @Column(name = "raw_payload")
    private String rawPayload;

    /** Template generated when this intel was converted into training content. */
    @Column(name = "converted_template_id")
    private UUID convertedTemplateId;

    /** When the intel was collected/ingested. */
    @Column(name = "collected_at")
    private Instant collectedAt;

    protected ThreatIntel() {
        // Required by JPA.
    }

    public ThreatIntel(UUID id, UUID tenantId, String source, String rawPayload,
                       UUID convertedTemplateId, Instant collectedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.source = source;
        this.rawPayload = rawPayload;
        this.convertedTemplateId = convertedTemplateId;
        this.collectedAt = collectedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getSource() {
        return source;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public UUID getConvertedTemplateId() {
        return convertedTemplateId;
    }

    public void setConvertedTemplateId(UUID convertedTemplateId) {
        this.convertedTemplateId = convertedTemplateId;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }
}
