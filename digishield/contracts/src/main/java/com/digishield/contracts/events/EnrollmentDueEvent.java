package com.digishield.contracts.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised by the overdue sweep for an assignment that needs chasing: either the
 * deadline is close, or it has already passed.
 *
 * <p>Carries {@code overdue} rather than leaving the reader to compare
 * {@code dueAt} against a clock, so the notification wording and the decision
 * behind it cannot drift apart.
 *
 * @param tenantId tenant the assignment belongs to
 * @param userId   the learner being chased
 * @param courseId the course they were assigned
 * @param dueAt    when the assignment is due
 * @param overdue  true when the deadline has already passed
 */
public record EnrollmentDueEvent(UUID tenantId, UUID userId, UUID courseId,
                                 Instant dueAt, boolean overdue) {
}
