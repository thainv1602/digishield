package com.digishield.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A completion certificate issued to a user for a course.
 */
@Entity
@Table(name = "certificate")
public class Certificate {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "serial", nullable = false)
    private String serial;

    @Column(name = "course_title", nullable = false)
    private String courseTitle;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "score")
    private Integer score;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "verify_url")
    private String verifyUrl;

    /** Default constructor required by JPA. */
    protected Certificate() {
    }

    public Certificate(UUID id, UUID tenantId, UUID userId, UUID courseId, String serial,
                       String courseTitle, String recipient, Integer score,
                       Instant issuedAt, Instant validUntil, String verifyUrl) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.courseId = courseId;
        this.serial = serial;
        this.courseTitle = courseTitle;
        this.recipient = recipient;
        this.score = score;
        this.issuedAt = issuedAt;
        this.validUntil = validUntil;
        this.verifyUrl = verifyUrl;
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

    public UUID getCourseId() {
        return courseId;
    }

    public String getSerial() {
        return serial;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public String getRecipient() {
        return recipient;
    }

    public Integer getScore() {
        return score;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public String getVerifyUrl() {
        return verifyUrl;
    }
}
