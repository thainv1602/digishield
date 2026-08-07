package com.digishield.learning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digishield.contracts.events.EnrollmentDueEvent;
import com.digishield.learning.domain.Enrollment;
import com.digishield.learning.domain.EnrollmentStatus;
import com.digishield.learning.infrastructure.EnrollmentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Unit tests for {@link EnrollmentDueJob}. */
class EnrollmentDueJobTest {

    private static final UUID TENANT = UUID.randomUUID();

    private final EnrollmentRepository repository = mock(EnrollmentRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final EnrollmentDueJob job = new EnrollmentDueJob(repository, events, 24, "0 0 8 * * *");

    private Enrollment enrollment(Instant dueAt, EnrollmentStatus status) {
        Enrollment e = new Enrollment(UUID.randomUUID(), TENANT, UUID.randomUUID(),
                UUID.randomUUID(), status, null);
        e.setDueAt(dueAt);
        return e;
    }

    private void given(Enrollment... rows) {
        when(repository.findByDueAtIsNotNullAndStatusNot(EnrollmentStatus.COMPLETED))
                .thenReturn(List.of(rows));
    }

    @Test
    void anAssignmentPastItsDateIsChasedAndMarkedOverdue() {
        Enrollment late = enrollment(Instant.now().minus(2, ChronoUnit.DAYS), EnrollmentStatus.ASSIGNED);
        given(late);

        job.sweep();

        assertThat(late.getStatus()).isEqualTo(EnrollmentStatus.OVERDUE);
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        assertThat(((EnrollmentDueEvent) published.getValue()).overdue()).isTrue();
    }

    @Test
    void anAssignmentDueTomorrowIsChasedButNotMarkedLate() {
        Enrollment soon = enrollment(Instant.now().plus(6, ChronoUnit.HOURS), EnrollmentStatus.ASSIGNED);
        given(soon);

        job.sweep();

        // Still on time: telling someone they are late a day early is how a
        // reminder stops being read.
        assertThat(soon.getStatus()).isEqualTo(EnrollmentStatus.ASSIGNED);
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        assertThat(((EnrollmentDueEvent) published.getValue()).overdue()).isFalse();
    }

    @Test
    void anAssignmentDueNextWeekIsLeftAlone() {
        given(enrollment(Instant.now().plus(7, ChronoUnit.DAYS), EnrollmentStatus.ASSIGNED));

        job.sweep();

        // any(Object.class), not any(): ApplicationEventPublisher overloads
        // publishEvent, and a bare any() binds to the ApplicationEvent one --
        // which this job never calls, so the check would pass either way.
        verify(events, never()).publishEvent(any(Object.class));
        verify(repository, never()).save(any(Enrollment.class));
    }

    @Test
    void anAlreadyOverdueRowIsNotWrittenAgainButIsStillChased() {
        Enrollment late = enrollment(Instant.now().minus(5, ChronoUnit.DAYS), EnrollmentStatus.OVERDUE);
        given(late);

        job.sweep();

        // The status is already right; rewriting it every morning would churn the
        // row for nothing. The reminder still goes out.
        verify(repository, never()).save(any(Enrollment.class));
        verify(events).publishEvent(any(Object.class));
    }
}
