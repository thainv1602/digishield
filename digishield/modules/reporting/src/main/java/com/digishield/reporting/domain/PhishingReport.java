package com.digishield.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A phishing report submitted by a user. Each report belongs to a tenant.
 */
@Entity
@Table(name = "phishing_report")
public class PhishingReport {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Lob
    @Column(name = "payload")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_label")
    private AiLabel aiLabel;

    @Column(name = "ai_confidence")
    private double aiConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status;

    /** Display name of the reporter (denormalized for the SOC inbox). */
    @Column(name = "reporter")
    private String reporter;

    /** Short subject line of the reported message. */
    @Column(name = "subject")
    private String subject;

    /** Sender address of the reported message. */
    @Column(name = "sender")
    private String sender;

    /** AI reasoning explaining the classification (SOC drawer). */
    @Lob
    @Column(name = "ai_reason")
    private String aiReason;

    /** Whether the message matched a known blacklist source. */
    @Column(name = "blacklist_match")
    private boolean blacklistMatch;

    /** When the report was submitted. */
    @Column(name = "reported_at")
    private Instant reportedAt;

    /** Whether this report has been flipped into training content. */
    @Column(name = "converted_to_training")
    private boolean convertedToTraining;

    protected PhishingReport() {
        // Required by JPA.
    }

    public PhishingReport(UUID id, UUID tenantId, UUID userId, String payload,
                          AiLabel aiLabel, double aiConfidence, ReportStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.payload = payload;
        this.aiLabel = aiLabel;
        this.aiConfidence = aiConfidence;
        this.status = status;
        this.reportedAt = Instant.now();
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public PhishingReport(UUID id, UUID tenantId, UUID userId, String payload,
                          AiLabel aiLabel, double aiConfidence, ReportStatus status,
                          String reporter, String subject, String sender, String aiReason,
                          boolean blacklistMatch, Instant reportedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.payload = payload;
        this.aiLabel = aiLabel;
        this.aiConfidence = aiConfidence;
        this.status = status;
        this.reporter = reporter;
        this.subject = subject;
        this.sender = sender;
        this.aiReason = aiReason;
        this.blacklistMatch = blacklistMatch;
        this.reportedAt = reportedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPayload() {
        return payload;
    }

    public AiLabel getAiLabel() {
        return aiLabel;
    }

    public void setAiLabel(AiLabel aiLabel) {
        this.aiLabel = aiLabel;
    }

    public double getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(double aiConfidence) {
        this.aiConfidence = aiConfidence;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public String getReporter() {
        return reporter;
    }

    public String getSubject() {
        return subject;
    }

    public String getSender() {
        return sender;
    }

    public String getAiReason() {
        return aiReason;
    }

    public boolean isBlacklistMatch() {
        return blacklistMatch;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public boolean isConvertedToTraining() {
        return convertedToTraining;
    }

    public void setConvertedToTraining(boolean convertedToTraining) {
        this.convertedToTraining = convertedToTraining;
    }
}
