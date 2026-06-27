package com.digishield.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * An enrollment of a user into a course, belonging to a tenant.
 */
@Entity
@Table(name = "enrollment")
public class Enrollment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EnrollmentStatus status;

    @Column(name = "score")
    private Integer score;

    /** Progress percentage (0..100). */
    @Column(name = "progress")
    private Integer progress;

    /** Default constructor required by JPA. */
    protected Enrollment() {
    }

    public Enrollment(UUID id, UUID tenantId, UUID userId, UUID courseId,
                      EnrollmentStatus status, Integer score) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.courseId = courseId;
        this.status = status;
        this.score = score;
    }

    public Enrollment(UUID id, UUID tenantId, UUID userId, UUID courseId,
                      EnrollmentStatus status, Integer score, Integer progress) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.courseId = courseId;
        this.status = status;
        this.score = score;
        this.progress = progress;
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

    public EnrollmentStatus getStatus() {
        return status;
    }

    public Integer getScore() {
        return score;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setStatus(EnrollmentStatus status) {
        this.status = status;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }
}
