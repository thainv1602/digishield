package com.digishield.learning.application;

import com.digishield.contracts.events.EnrollmentAssignedEvent;
import com.digishield.learning.api.EnrollmentView;
import com.digishield.learning.domain.Course;
import com.digishield.learning.domain.CourseLevel;
import com.digishield.learning.domain.Enrollment;
import com.digishield.learning.domain.EnrollmentStatus;
import com.digishield.learning.domain.PointRule;
import com.digishield.learning.infrastructure.CourseRepository;
import com.digishield.learning.infrastructure.EnrollmentRepository;
import com.digishield.learning.infrastructure.PointRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LearningServiceImpl}.
 * <p>
 * Pure Mockito unit tests: no Spring context, no real database.
 */
@ExtendWith(MockitoExtension.class)
class LearningServiceImplTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private PointRuleRepository pointRuleRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private com.digishield.learning.infrastructure.CompliancePolicyRepository compliancePolicyRepository;

    @InjectMocks
    private LearningServiceImpl learningService;

    @Captor
    private ArgumentCaptor<Enrollment> enrollmentCaptor;

    @Captor
    private ArgumentCaptor<EnrollmentAssignedEvent> eventCaptor;

    @Test
    void assign_whenNotYetEnrolled_persistsEnrollmentAndPublishesEvent() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(enrollmentRepository.findByTenantIdAndUserIdAndCourseId(TENANT_ID, userId, courseId))
                .thenReturn(Optional.empty());
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        EnrollmentView view = learningService.assign(TENANT_ID, userId, courseId);

        // Assert: a new ASSIGNED enrollment was persisted
        verify(enrollmentRepository).save(enrollmentCaptor.capture());
        Enrollment persisted = enrollmentCaptor.getValue();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(persisted.getUserId()).isEqualTo(userId);
        assertThat(persisted.getCourseId()).isEqualTo(courseId);
        assertThat(persisted.getStatus()).isEqualTo(EnrollmentStatus.ASSIGNED);
        assertThat(persisted.getScore()).isNull();

        // Assert: the cross-module event was published with the correct fields
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        EnrollmentAssignedEvent event = eventCaptor.getValue();
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.courseId()).isEqualTo(courseId);

        // Assert: returned view
        assertThat(view.status()).isEqualTo("ASSIGNED");
        assertThat(view.userId()).isEqualTo(userId);
        assertThat(view.courseId()).isEqualTo(courseId);
    }

    @Test
    void assign_whenAlreadyEnrolled_doesNotSaveButStillPublishesEvent() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Enrollment existing = new Enrollment(
                UUID.randomUUID(), TENANT_ID, userId, courseId, EnrollmentStatus.IN_PROGRESS, 50);
        when(enrollmentRepository.findByTenantIdAndUserIdAndCourseId(TENANT_ID, userId, courseId))
                .thenReturn(Optional.of(existing));

        // Act
        EnrollmentView view = learningService.assign(TENANT_ID, userId, courseId);

        // Assert: no new enrollment persisted
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
        // Assert: event still published (idempotent assignment semantics)
        verify(eventPublisher).publishEvent(any(EnrollmentAssignedEvent.class));
        assertThat(view.status()).isEqualTo("IN_PROGRESS");
        assertThat(view.score()).isEqualTo(50);
    }

    @Test
    void autoEnroll_assignsTenantsFirstCourseAndPublishesEvent() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = new Course(courseId, TENANT_ID, "Phishing 101", CourseLevel.BEGINNER, "en");
        when(courseRepository.findFirstByTenantId(TENANT_ID)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByTenantIdAndUserIdAndCourseId(TENANT_ID, userId, courseId))
                .thenReturn(Optional.empty());
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        EnrollmentView view = learningService.autoEnroll(TENANT_ID, userId);

        // Assert: enrollment persisted against the first course
        verify(enrollmentRepository).save(enrollmentCaptor.capture());
        assertThat(enrollmentCaptor.getValue().getCourseId()).isEqualTo(courseId);

        // Assert: event published for the first course
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().courseId()).isEqualTo(courseId);
        assertThat(view.courseId()).isEqualTo(courseId);
    }

    @Test
    void autoEnroll_whenNoCourseForTenant_throwsIllegalState() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(courseRepository.findFirstByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        // Act + Assert
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> learningService.autoEnroll(TENANT_ID, userId))
                .isInstanceOf(IllegalStateException.class);
        verify(enrollmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void listPointRules_mapsEntitiesToViews() {
        // Arrange
        PointRule report = new PointRule(
                UUID.randomUUID(), TENANT_ID, "report_confirmed", "Báo cáo email lừa đảo đúng", 50);
        PointRule click = new PointRule(
                UUID.randomUUID(), TENANT_ID, "simulation_clicked", "Bấm link mô phỏng", -5);
        when(pointRuleRepository.findByTenantIdOrderByPointsDesc(TENANT_ID))
                .thenReturn(java.util.List.of(report, click));

        // Act
        var views = learningService.listPointRules(TENANT_ID);

        // Assert
        assertThat(views).hasSize(2);
        assertThat(views.get(0).action()).isEqualTo("report_confirmed");
        assertThat(views.get(0).points()).isEqualTo(50);
        assertThat(views.get(1).points()).isEqualTo(-5);
    }

    // ---- Compliance: completion is derived, never stored -------------------

    private static final UUID COURSE_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID COURSE_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

    /**
     * course A: 2 of 3 done (67%), course B: 1 of 2 done (50%).
     * Only USER_1 has finished everything they were given.
     */
    private void givenEnrollments() {
        when(enrollmentRepository.findByTenantId(TENANT_ID)).thenReturn(java.util.List.of(
                enrollment(USER_1, COURSE_A, EnrollmentStatus.COMPLETED),
                enrollment(USER_2, COURSE_A, EnrollmentStatus.COMPLETED),
                enrollment(USER_3, COURSE_A, EnrollmentStatus.ASSIGNED),
                enrollment(USER_1, COURSE_B, EnrollmentStatus.COMPLETED),
                enrollment(USER_2, COURSE_B, EnrollmentStatus.IN_PROGRESS)));
    }

    @Test
    void listCompliancePolicies_reportsEachCoursesRealCompletion() {
        givenEnrollments();
        when(compliancePolicyRepository.findByTenantId(TENANT_ID)).thenReturn(java.util.List.of(
                policy(COURSE_A), policy(COURSE_B)));

        var views = learningService.listCompliancePolicies(TENANT_ID);

        // 2/3 and 1/2 — read off the enrollments, not off a stored column.
        assertThat(views).extracting(com.digishield.learning.api.CompliancePolicyView::completionPct)
                .containsExactly(67, 50);
    }

    @Test
    void policyWithoutACourse_fallsBackToOverallCompletion() {
        givenEnrollments();
        when(compliancePolicyRepository.findByTenantId(TENANT_ID))
                .thenReturn(java.util.List.of(policy(null)));

        var views = learningService.listCompliancePolicies(TENANT_ID);

        // 3 of 5 enrollments completed overall.
        assertThat(views.get(0).completionPct()).isEqualTo(60);
    }

    @Test
    void policyWhoseCourseNobodyIsEnrolledIn_readsZeroRatherThanInventingANumber() {
        givenEnrollments();
        UUID unusedCourse = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
        when(compliancePolicyRepository.findByTenantId(TENANT_ID))
                .thenReturn(java.util.List.of(policy(unusedCourse)));

        assertThat(learningService.listCompliancePolicies(TENANT_ID).get(0).completionPct())
                .isZero();
    }

    @Test
    void getComplianceStatus_countsCompliantPeopleIndividually() {
        givenEnrollments();
        when(compliancePolicyRepository.findByTenantId(TENANT_ID)).thenReturn(java.util.List.of(
                policy(COURSE_A), policy(COURSE_B)));
        when(enrollmentRepository.countDistinctUsers(TENANT_ID)).thenReturn(3L);

        com.digishield.learning.api.ComplianceStatusView status =
                learningService.getComplianceStatus(TENANT_ID);

        assertThat(status.totalCount()).isEqualTo(3);
        // Derived per person: only USER_1 has nothing outstanding. The old code
        // multiplied the average by head count, which invents people.
        assertThat(status.compliantCount()).isEqualTo(1);
        assertThat(status.overdueCount()).isEqualTo(2);
        assertThat(status.compliantPct()).isEqualTo(58.5);   // mean of 67 and 50
        assertThat(status.dueSoonCount()).isEqualTo(2);      // both in 50..89
        assertThat(status.completedCount()).isZero();        // neither reaches 90
    }

    @Test
    void getComplianceStatus_withNoPoliciesIsAllZeroes() {
        when(compliancePolicyRepository.findByTenantId(TENANT_ID))
                .thenReturn(java.util.List.of());

        com.digishield.learning.api.ComplianceStatusView status =
                learningService.getComplianceStatus(TENANT_ID);

        assertThat(status.totalCount()).isZero();
        assertThat(status.compliantPct()).isZero();
    }

    private Enrollment enrollment(UUID userId, UUID courseId, EnrollmentStatus status) {
        return new Enrollment(UUID.randomUUID(), TENANT_ID, userId, courseId, status, null);
    }

    private com.digishield.learning.domain.CompliancePolicy policy(UUID courseId) {
        return new com.digishield.learning.domain.CompliancePolicy(
                UUID.randomUUID(), TENANT_ID, "Policy", "GDPR", "before_due:7d", true, courseId);
    }
}
