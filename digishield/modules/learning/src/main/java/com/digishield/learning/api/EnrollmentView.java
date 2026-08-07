package com.digishield.learning.api;

import java.time.Instant;
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
 * @param dueAt       when the assignment is due; null for assignments made
 *                    before deadlines existed
 */
public record EnrollmentView(UUID id, UUID tenantId, UUID userId, UUID courseId,
                             String courseTitle, String status, Integer progress,
                             Integer score,
                             @com.fasterxml.jackson.annotation.JsonProperty("due_at") Instant dueAt) {
}
