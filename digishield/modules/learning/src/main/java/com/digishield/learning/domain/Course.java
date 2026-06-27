package com.digishield.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A security awareness training course belonging to a tenant.
 */
@Entity
@Table(name = "course")
public class Course {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private CourseLevel level;

    @Column(name = "lang", nullable = false)
    private String lang;

    /** Estimated total duration in minutes (used by the catalog "meta" line). */
    @Column(name = "duration_min")
    private Integer durationMin;

    /** Number of lessons in the course (used by the catalog "meta" line). */
    @Column(name = "lesson_count")
    private Integer lessonCount;

    /**
     * Ordering position used to lock courses progressively in the catalog
     * (a course is "locked" until the previous one is completed).
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** Default constructor required by JPA. */
    protected Course() {
    }

    public Course(UUID id, UUID tenantId, String title, CourseLevel level, String lang) {
        this.id = id;
        this.tenantId = tenantId;
        this.title = title;
        this.level = level;
        this.lang = lang;
    }

    public Course(UUID id, UUID tenantId, String title, CourseLevel level, String lang,
                  Integer durationMin, Integer lessonCount, Integer sortOrder) {
        this.id = id;
        this.tenantId = tenantId;
        this.title = title;
        this.level = level;
        this.lang = lang;
        this.durationMin = durationMin;
        this.lessonCount = lessonCount;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getTitle() {
        return title;
    }

    public CourseLevel getLevel() {
        return level;
    }

    public String getLang() {
        return lang;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public Integer getLessonCount() {
        return lessonCount;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setLevel(CourseLevel level) {
        this.level = level;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }
}
