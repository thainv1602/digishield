package com.digishield.learning.application;

import com.digishield.contracts.events.EnrollmentDueEvent;
import com.digishield.learning.domain.Enrollment;
import com.digishield.learning.domain.EnrollmentStatus;
import com.digishield.learning.infrastructure.EnrollmentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chases training assignments that are late, or about to be.
 *
 * <p>Only active in the {@code scheduler} profile, which runs as a single
 * replica. Several replicas sweeping at once would send the same person the same
 * reminder several times, which teaches them to ignore it.
 *
 * <p>The sweep does two things and they are deliberately separate. It writes
 * {@code OVERDUE} onto rows whose deadline has passed, so a report or an export
 * reading the column agrees with what the portal shows; and it raises an event
 * per assignment that needs chasing, which the notification module turns into a
 * reminder. Reading the status is never what decides lateness — {@code
 * Enrollment.isOverdue} does, against the clock — the write is bookkeeping that
 * catches up.
 */
@Component
@Profile("scheduler")
public class EnrollmentDueJob {

    private static final Logger LOG = LoggerFactory.getLogger(EnrollmentDueJob.class);

    private final EnrollmentRepository enrollments;
    private final ApplicationEventPublisher events;
    private final int remindBeforeHours;
    private final String cron;

    public EnrollmentDueJob(EnrollmentRepository enrollments,
                            ApplicationEventPublisher events,
                            @Value("${digishield.learning.due-sweep.remind-before-hours:24}")
                            int remindBeforeHours,
                            @Value("${digishield.learning.due-sweep.cron:0 0 8 * * *}") String cron) {
        this.enrollments = enrollments;
        this.events = events;
        this.remindBeforeHours = remindBeforeHours;
        this.cron = cron;
    }

    @Scheduled(cron = "${digishield.learning.due-sweep.cron:0 0 8 * * *}")
    @Transactional
    public void sweep() {
        Instant now = Instant.now();
        Instant soon = now.plus(remindBeforeHours, ChronoUnit.HOURS);

        List<Enrollment> open = enrollments.findByDueAtIsNotNullAndStatusNot(EnrollmentStatus.COMPLETED);
        int late = 0;
        int upcoming = 0;

        for (Enrollment e : open) {
            boolean overdue = e.isOverdue(now);
            if (!overdue && e.getDueAt().isAfter(soon)) {
                // Not due for a while yet. Chasing now would spend the one piece
                // of attention this reminder gets on a day nothing is required.
                continue;
            }
            if (overdue && e.getStatus() != EnrollmentStatus.OVERDUE) {
                e.setStatus(EnrollmentStatus.OVERDUE);
                enrollments.save(e);
            }
            events.publishEvent(new EnrollmentDueEvent(
                    e.getTenantId(), e.getUserId(), e.getCourseId(), e.getDueAt(), overdue));
            if (overdue) {
                late++;
            } else {
                upcoming++;
            }
        }
        LOG.info("[learning] Due sweep (cron {}): {} overdue, {} due within {}h, {} open with a deadline",
                cron, late, upcoming, remindBeforeHours, open.size());
    }
}
