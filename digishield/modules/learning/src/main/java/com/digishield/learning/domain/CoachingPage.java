package com.digishield.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A just-in-time coaching page shown after a risky action (e.g. clicking a
 * simulated phishing link), optionally derived from a simulation template.
 */
@Entity
@Table(name = "coaching_page")
public class CoachingPage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Simulation template this coaching page is associated with (may be null). */
    @Column(name = "template_id")
    private UUID templateId;

    /** Reference to the coaching content (markdown/URL/asset key). */
    @Column(name = "content_ref")
    private String contentRef;

    /** Recognizable warning signs to display, serialized as a JSON document. */
    @Column(name = "signals_json", length = 8000)
    private String signalsJson;

    /** Default constructor required by JPA. */
    protected CoachingPage() {
    }

    public CoachingPage(UUID id, UUID tenantId, UUID templateId, String contentRef,
                        String signalsJson) {
        this.id = id;
        this.tenantId = tenantId;
        this.templateId = templateId;
        this.contentRef = contentRef;
        this.signalsJson = signalsJson;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public String getContentRef() {
        return contentRef;
    }

    public String getSignalsJson() {
        return signalsJson;
    }
}
