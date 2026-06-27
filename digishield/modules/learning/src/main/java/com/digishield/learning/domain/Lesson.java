package com.digishield.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A single lesson belonging to a course, rendered by the Lesson Player.
 * <p>
 * The lesson body and checkpoint outline are stored as simple text columns; the
 * checkpoints are kept as a JSON-ish text payload that the web layer parses into
 * a list of checkpoint labels.
 */
@Entity
@Table(name = "lesson")
public class Lesson {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", length = 4000)
    private String body;

    @Column(name = "example_title")
    private String exampleTitle;

    @Column(name = "example_body", length = 2000)
    private String exampleBody;

    @Column(name = "closing", length = 2000)
    private String closing;

    /** Comma-separated checkpoint labels (e.g. "Khái niệm,Cách nhận biết,..."). */
    @Column(name = "checkpoints", length = 1000)
    private String checkpoints;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Column(name = "sort_order")
    private Integer sortOrder;

    /** Default constructor required by JPA. */
    protected Lesson() {
    }

    public Lesson(UUID id, UUID tenantId, UUID courseId, String title, String body,
                  String exampleTitle, String exampleBody, String closing,
                  String checkpoints, Integer durationMin, Integer sortOrder) {
        this.id = id;
        this.tenantId = tenantId;
        this.courseId = courseId;
        this.title = title;
        this.body = body;
        this.exampleTitle = exampleTitle;
        this.exampleBody = exampleBody;
        this.closing = closing;
        this.checkpoints = checkpoints;
        this.durationMin = durationMin;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getExampleTitle() {
        return exampleTitle;
    }

    public String getExampleBody() {
        return exampleBody;
    }

    public String getClosing() {
        return closing;
    }

    public String getCheckpoints() {
        return checkpoints;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }
}
