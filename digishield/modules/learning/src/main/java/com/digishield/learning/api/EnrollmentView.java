package com.digishield.learning.api;

import java.util.UUID;

/**
 * Public view describing an enrollment.
 *
 * @param id          enrollment identifier
 * @param tenantId    tenant of the enrollment
 * @param userId      enrolled user
 * @param courseId    assigned course
 * @param courseTitle title of the assigned course (denormalized for the portal)
 * @param status      enrollment status (assigned|in_progress|completed|overdue, lower-case)
 * @param progress    progress percentage (0..100, may be null)
 * @param score       score (may be null if the quiz has not been taken)
 */
public record EnrollmentView(UUID id, UUID tenantId, UUID userId, UUID courseId,
                             String courseTitle, String status, Integer progress,
                             Integer score) {
}
