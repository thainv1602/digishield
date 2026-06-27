package com.digishield.learning.api;

import java.util.UUID;

/**
 * Public view describing a course for the catalog.
 *
 * @param id          course identifier
 * @param tenantId    tenant of the course
 * @param title       title (also exposed as {@code domain} in the OpenAPI Course schema)
 * @param level       level (basic|beginner|intermediate|advanced)
 * @param lang        language
 * @param durationMin estimated total duration in minutes
 * @param lessonCount number of lessons
 * @param progress    learner progress percentage (0..100, may be null)
 * @param status      derived state for the catalog: completed|in_progress|locked
 */
public record CourseView(UUID id, UUID tenantId, String title, String level, String lang,
                         Integer durationMin, Integer lessonCount,
                         Integer progress, String status) {
}
