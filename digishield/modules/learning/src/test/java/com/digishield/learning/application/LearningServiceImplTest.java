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
    private com.digishield.learning.infrastructure.LessonRepository lessonRepository;

    @Mock
    private PointsAwarder pointsAwarder;

    @Mock
    private com.digishield.learning.infrastructure.CertificateRepository certificateRepository;

    @Mock
    private com.digishield.learning.api.LearnerDirectory learnerDirectory;

    @Mock
    private com.digishield.learning.api.BehaviourHistory behaviourHistory;

    @Mock
    private com.digishield.learning.api.PassMarkProvider passMarkProvider;

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
    void completeQuizIssuesACertificateOnPassing() {
        UUID enrollmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Enrollment enrollment = new Enrollment(enrollmentId, TENANT_ID, userId, courseId,
                EnrollmentStatus.IN_PROGRESS, null);
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
        when(certificateRepository.findByTenantIdAndUserId(TENANT_ID, userId))
                .thenReturn(java.util.List.of());
        when(courseRepository.findByTenantIdAndId(TENANT_ID, courseId))
                .thenReturn(Optional.of(new Course(courseId, TENANT_ID, "An toàn thông tin",
                        CourseLevel.BASIC, "vi")));
        when(learnerDirectory.find(userId)).thenReturn(Optional.of(
                new com.digishield.learning.api.LearnerDirectory.Learner("Nguyễn Văn A", "IT")));
        givenPassMark(70);

        learningService.completeQuiz(TENANT_ID, enrollmentId, 85);

        ArgumentCaptor<com.digishield.learning.domain.Certificate> saved =
                ArgumentCaptor.forClass(com.digishield.learning.domain.Certificate.class);
        verify(certificateRepository).save(saved.capture());
        // The table had one writer, a dev seeder, so people who had earned a
        // certificate saw an empty screen.
        assertThat(saved.getValue().getRecipient()).isEqualTo("Nguyễn Văn A");
        assertThat(saved.getValue().getCourseTitle()).isEqualTo("An toàn thông tin");
        assertThat(saved.getValue().getScore()).isEqualTo(85);
        assertThat(saved.getValue().getSerial()).startsWith("DS-");
    }

    @Test
    void completeQuizIssuesNoCertificateOnFailing() {
        UUID enrollmentId = UUID.randomUUID();
        Enrollment enrollment = new Enrollment(enrollmentId, TENANT_ID, UUID.randomUUID(),
                UUID.randomUUID(), EnrollmentStatus.IN_PROGRESS, null);
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
        givenPassMark(80);

        learningService.completeQuiz(TENANT_ID, enrollmentId, 79);

        verify(certificateRepository, never()).save(any());
    }

    @Test
    void completeQuizDoesNotMintASecondCertificateForTheSameCourse() {
        UUID enrollmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Enrollment enrollment = new Enrollment(enrollmentId, TENANT_ID, userId, courseId,
                EnrollmentStatus.IN_PROGRESS, null);
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
        when(certificateRepository.findByTenantIdAndUserId(TENANT_ID, userId))
                .thenReturn(java.util.List.of(new com.digishield.learning.domain.Certificate(
                        UUID.randomUUID(), TENANT_ID, userId, courseId, "DS-2026-AAAA-BBBB",
                        "An toàn thông tin", "Nguyễn Văn A", 90,
                        java.time.Instant.now(), java.time.Instant.now(), null)));
        givenPassMark(70);

        learningService.completeQuiz(TENANT_ID, enrollmentId, 95);

        // A course reopened by a repeat offence is passed again; that is one
        // certificate re-earned, not two held.
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void autoEnrollSendsARepeatClickerUpALevel() {
        UUID userId = UUID.randomUUID();
        Course basic = new Course(UUID.randomUUID(), TENANT_ID, "Nhập môn", CourseLevel.BASIC, "vi");
        Course harder = new Course(UUID.randomUUID(), TENANT_ID, "Nâng cao", CourseLevel.INTERMEDIATE, "vi");
        when(courseRepository.findByTenantIdOrderBySortOrderAsc(TENANT_ID))
                .thenReturn(java.util.List.of(basic, harder));
        givenClicks(2);
        when(enrollmentRepository.findByTenantIdAndUserIdAndCourseId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        learningService.autoEnroll(TENANT_ID, userId);

        // A second click used to be answered with the same introduction as the
        // first, because autoEnroll took whatever course came back first.
        verify(enrollmentRepository).save(enrollmentCaptor.capture());
        assertThat(enrollmentCaptor.getValue().getCourseId()).isEqualTo(harder.getId());
    }

    @Test
    void autoEnrollStopsAtTheHardestCourseTheTenantHas() {
        UUID userId = UUID.randomUUID();
        Course basic = new Course(UUID.randomUUID(), TENANT_ID, "Nhập môn", CourseLevel.BASIC, "vi");
        Course harder = new Course(UUID.randomUUID(), TENANT_ID, "Nâng cao", CourseLevel.INTERMEDIATE, "vi");
        when(courseRepository.findByTenantIdOrderBySortOrderAsc(TENANT_ID))
                .thenReturn(java.util.List.of(basic, harder));
        givenClicks(9);
        when(enrollmentRepository.findByTenantIdAndUserIdAndCourseId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        learningService.autoEnroll(TENANT_ID, userId);

        // The ladder cannot climb past the courses that exist.
        verify(enrollmentRepository).save(enrollmentCaptor.capture());
        assertThat(enrollmentCaptor.getValue().getCourseId()).isEqualTo(harder.getId());
    }

    @Test
    void assignReopensACourseTheyHadAlreadyFinished() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Enrollment finished = new Enrollment(UUID.randomUUID(), TENANT_ID, userId, courseId,
                EnrollmentStatus.COMPLETED, 100);
        finished.setProgress(100);
        when(enrollmentRepository.findByTenantIdAndUserIdAndCourseId(TENANT_ID, userId, courseId))
                .thenReturn(Optional.of(finished));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        learningService.assign(TENANT_ID, userId, courseId);

        // Being assigned it again means it is needed again. This used to return
        // the completed record untouched, so a repeat offence produced no
        // training and the person stayed compliant on the first attempt.
        assertThat(finished.getStatus()).isEqualTo(EnrollmentStatus.ASSIGNED);
        assertThat(finished.getProgress()).isZero();
        assertThat(finished.getScore()).isNull();
    }

    private void givenClicks(int clicks) {
        when(behaviourHistory.simulationClicks(any(), any())).thenReturn(clicks);
    }

    @Test
    void completeQuizPaysOnceForCompletionAndOnceForPassing() {
        UUID enrollmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Enrollment enrollment = new Enrollment(enrollmentId, TENANT_ID, userId, UUID.randomUUID(),
                EnrollmentStatus.IN_PROGRESS, null);
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

        givenPassMark(70);
        learningService.completeQuiz(TENANT_ID, enrollmentId, 80);

        verify(pointsAwarder).award(TENANT_ID, userId,
                com.digishield.learning.domain.PointAction.LESSON_COMPLETED);
        verify(pointsAwarder).award(TENANT_ID, userId,
                com.digishield.learning.domain.PointAction.QUIZ_PASSED);
    }

    @Test
    void completeQuizPaysNothingForARetakeOfAFinishedCourse() {
        UUID enrollmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Enrollment enrollment = new Enrollment(enrollmentId, TENANT_ID, userId, UUID.randomUUID(),
                EnrollmentStatus.COMPLETED, null);
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

        givenPassMark(70);
        learningService.completeQuiz(TENANT_ID, enrollmentId, 100);

        // Otherwise anyone could resubmit a finished course to farm points.
        verify(pointsAwarder, never()).award(any(), any(), any());
    }

    @Test
    void completeQuizBelowThePassMarkIsNotFinishing() {
        UUID enrollmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Enrollment enrollment = new Enrollment(enrollmentId, TENANT_ID, userId, UUID.randomUUID(),
                EnrollmentStatus.IN_PROGRESS, null);
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
        givenPassMark(80);

        learningService.completeQuiz(TENANT_ID, enrollmentId, 79);

        // This used to mark the course COMPLETED whatever the score, so a
        // failing learner counted as trained, counted as compliant, and would
        // have been paid for it.
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        verify(pointsAwarder, never()).award(any(), any(), any());
    }

    @Test
    void completeQuizUsesTheTenantsConfiguredPassMarkNotAFixedOne() {
        UUID enrollmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Enrollment enrollment = new Enrollment(enrollmentId, TENANT_ID, userId, UUID.randomUUID(),
                EnrollmentStatus.IN_PROGRESS, null);
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
        // business_thresholds.pass_score_pct was settable and read by nobody, so
        // raising it in settings changed nothing. 75 must now fail at 80.
        givenPassMark(80);

        learningService.completeQuiz(TENANT_ID, enrollmentId, 75);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
    }

    @Test
    void completeQuizFailingAResitDoesNotUndoAnEarlierPass() {
        UUID enrollmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Enrollment enrollment = new Enrollment(enrollmentId, TENANT_ID, userId, UUID.randomUUID(),
                EnrollmentStatus.COMPLETED, null);
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
        givenPassMark(80);

        learningService.completeQuiz(TENANT_ID, enrollmentId, 40);

        // Sitting it again out of interest must not strip a completion already
        // earned, nor the compliance that rests on it.
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        verify(pointsAwarder, never()).award(any(), any(), any());
    }

    private void givenPassMark(int pct) {
        when(passMarkProvider.passScorePct(TENANT_ID)).thenReturn(pct);
    }

    @Test
    void updateProgressPaysOnlyOnTheTransitionToComplete() {
        UUID enrollmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Enrollment enrollment = new Enrollment(enrollmentId, TENANT_ID, userId, UUID.randomUUID(),
                EnrollmentStatus.COMPLETED, null);
        when(enrollmentRepository.findByTenantIdAndId(TENANT_ID, enrollmentId))
                .thenReturn(Optional.of(enrollment));

        learningService.updateProgress(TENANT_ID, enrollmentId, 100);

        // Clients report 100% repeatedly; paying each time would inflate every
        // score on the leaderboard.
        verify(pointsAwarder, never()).award(any(), any(), any());
    }

    @Test
    void createCourseDerivesLessonCountRatherThanTrustingTheRequest() {
        when(courseRepository.findByTenantId(TENANT_ID)).thenReturn(java.util.List.of());
        when(courseRepository.save(any(Course.class))).thenAnswer(i -> i.getArgument(0));

        // A caller claiming 99 lessons must not be believed: the count is a fact
        // about the lessons, and a new course has none.
        learningService.createCourse(TENANT_ID, new com.digishield.learning.api.CourseView(
                null, null, "An toàn thông tin", "basic", "vi", 30, 99, null, null));

        ArgumentCaptor<Course> saved = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(saved.capture());
        assertThat(saved.getValue().getLessonCount()).isZero();
        assertThat(saved.getValue().getTitle()).isEqualTo("An toàn thông tin");
    }

    @Test
    void createCourseRejectsABlankTitle() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        learningService.createCourse(TENANT_ID, new com.digishield.learning.api.CourseView(
                                null, null, "   ", "basic", "vi", 30, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(courseRepository, never()).save(any());
    }

    @Test
    void deleteCourseRefusesWhileAnyoneIsEnrolled() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course(courseId, TENANT_ID, "Khoá cũ", CourseLevel.BASIC, "vi");
        when(courseRepository.findByTenantIdAndId(TENANT_ID, courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByTenantIdAndCourseId(TENANT_ID, courseId)).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        learningService.deleteCourse(TENANT_ID, courseId))
                .isInstanceOf(IllegalStateException.class);

        // Learner progress points at this course; deleting it would leave those
        // records referring to nothing.
        verify(courseRepository, never()).delete(any());
        verify(lessonRepository, never()).deleteByTenantIdAndCourseId(any(), any());
    }

    @Test
    void deleteCourseRemovesItsLessonsWhenNobodyIsEnrolled() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course(courseId, TENANT_ID, "Khoá rỗng", CourseLevel.BASIC, "vi");
        when(courseRepository.findByTenantIdAndId(TENANT_ID, courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByTenantIdAndCourseId(TENANT_ID, courseId)).thenReturn(false);

        learningService.deleteCourse(TENANT_ID, courseId);

        verify(lessonRepository).deleteByTenantIdAndCourseId(TENANT_ID, courseId);
        verify(courseRepository).delete(course);
    }

    @Test
    void createLessonRecountsTheCourseFromTheLessonsThatExist() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course(courseId, TENANT_ID, "Khoá", CourseLevel.BASIC, "vi", 30, 0, 1);
        when(courseRepository.findByTenantIdAndId(TENANT_ID, courseId)).thenReturn(Optional.of(course));
        when(lessonRepository.countByTenantIdAndCourseId(TENANT_ID, courseId)).thenReturn(0, 1);
        when(lessonRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(courseRepository.save(any(Course.class))).thenAnswer(i -> i.getArgument(0));

        learningService.createLesson(TENANT_ID, new com.digishield.learning.api.LessonView(
                null, courseId, "Bài 1", "nội dung", null, null, null, 10, 0, java.util.List.of()));

        // The stored count is rewritten from the lessons, so it cannot drift
        // away from them the way lesson_count would if it were set by hand.
        assertThat(course.getLessonCount()).isEqualTo(1);
    }

    @Test
    void assignWhenNotYetEnrolledPersistsEnrollmentAndPublishesEvent() {
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
        assertThat(view.status()).isEqualTo("assigned");
        assertThat(view.userId()).isEqualTo(userId);
        assertThat(view.courseId()).isEqualTo(courseId);
    }

    @Test
    void assignWhenAlreadyEnrolledDoesNotSaveButStillPublishesEvent() {
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
        assertThat(view.status()).isEqualTo("in_progress");
        assertThat(view.score()).isEqualTo(50);
    }

    @Test
    void autoEnrollAssignsTenantsFirstCourseAndPublishesEvent() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = new Course(courseId, TENANT_ID, "Phishing 101", CourseLevel.BEGINNER, "en");
        when(courseRepository.findByTenantIdOrderBySortOrderAsc(TENANT_ID))
                .thenReturn(java.util.List.of(course));
        givenClicks(1);
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
    void autoEnrollWhenNoCourseForTenantThrowsIllegalState() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(courseRepository.findByTenantIdOrderBySortOrderAsc(TENANT_ID))
                .thenReturn(java.util.List.of());
        when(courseRepository.findByTenantId(TENANT_ID)).thenReturn(java.util.List.of());

        // Act + Assert
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> learningService.autoEnroll(TENANT_ID, userId))
                .isInstanceOf(IllegalStateException.class);
        verify(enrollmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void listPointRulesMapsEntitiesToViews() {
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
    void listCompliancePoliciesReportsEachCoursesRealCompletion() {
        givenEnrollments();
        when(compliancePolicyRepository.findByTenantId(TENANT_ID)).thenReturn(java.util.List.of(
                policy(COURSE_A), policy(COURSE_B)));

        var views = learningService.listCompliancePolicies(TENANT_ID);

        // 2/3 and 1/2 — read off the enrollments, not off a stored column.
        assertThat(views).extracting(com.digishield.learning.api.CompliancePolicyView::completionPct)
                .containsExactly(67, 50);
    }

    @Test
    void policyWithoutACourseFallsBackToOverallCompletion() {
        givenEnrollments();
        when(compliancePolicyRepository.findByTenantId(TENANT_ID))
                .thenReturn(java.util.List.of(policy(null)));

        var views = learningService.listCompliancePolicies(TENANT_ID);

        // 3 of 5 enrollments completed overall.
        assertThat(views.get(0).completionPct()).isEqualTo(60);
    }

    @Test
    void policyWhoseCourseNobodyIsEnrolledInReadsZeroRatherThanInventingANumber() {
        givenEnrollments();
        UUID unusedCourse = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
        when(compliancePolicyRepository.findByTenantId(TENANT_ID))
                .thenReturn(java.util.List.of(policy(unusedCourse)));

        assertThat(learningService.listCompliancePolicies(TENANT_ID).get(0).completionPct())
                .isZero();
    }

    @Test
    void getComplianceStatusCountsCompliantPeopleIndividually() {
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
    void getComplianceStatusWithNoPoliciesIsAllZeroes() {
        when(compliancePolicyRepository.findByTenantId(TENANT_ID))
                .thenReturn(java.util.List.of());

        com.digishield.learning.api.ComplianceStatusView status =
                learningService.getComplianceStatus(TENANT_ID);

        assertThat(status.totalCount()).isZero();
        assertThat(status.compliantPct()).isZero();
    }

    @Test
    void completionPctIsCountedInTheDatabaseNotByLoadingEnrollments() {
        when(enrollmentRepository.countByTenantId(TENANT_ID)).thenReturn(8L);
        when(enrollmentRepository.countByTenantIdAndStatus(TENANT_ID, EnrollmentStatus.COMPLETED))
                .thenReturn(6L);

        assertThat(learningService.completionPct(TENANT_ID)).isEqualTo(75);

        // The tile is one number; it must not pull the rows behind it. Loading
        // the enrollments (and the courses, to label them) is what this replaced.
        verify(enrollmentRepository, never()).findByTenantId(any());
        verify(courseRepository, never()).findByTenantId(any());
    }

    @Test
    void completionPctRoundsRatherThanTruncates() {
        // 5/8 = 62.5%. Truncation would report 62 and quietly under-state it.
        when(enrollmentRepository.countByTenantId(TENANT_ID)).thenReturn(8L);
        when(enrollmentRepository.countByTenantIdAndStatus(TENANT_ID, EnrollmentStatus.COMPLETED))
                .thenReturn(5L);

        assertThat(learningService.completionPct(TENANT_ID)).isEqualTo(63);
    }

    @Test
    void completionPctWithNobodyEnrolledIsZeroAndAsksNoFurtherQuestions() {
        when(enrollmentRepository.countByTenantId(TENANT_ID)).thenReturn(0L);

        assertThat(learningService.completionPct(TENANT_ID)).isZero();

        // Nothing to divide by, so the second count is never issued — and the
        // division that would have thrown never happens.
        verify(enrollmentRepository, never()).countByTenantIdAndStatus(any(), any());
    }

    @Test
    void completionPctIsOneHundredWhenEveryEnrollmentIsDone() {
        when(enrollmentRepository.countByTenantId(TENANT_ID)).thenReturn(4L);
        when(enrollmentRepository.countByTenantIdAndStatus(TENANT_ID, EnrollmentStatus.COMPLETED))
                .thenReturn(4L);

        assertThat(learningService.completionPct(TENANT_ID)).isEqualTo(100);
    }

    private Enrollment enrollment(UUID userId, UUID courseId, EnrollmentStatus status) {
        return new Enrollment(UUID.randomUUID(), TENANT_ID, userId, courseId, status, null);
    }

    private com.digishield.learning.domain.CompliancePolicy policy(UUID courseId) {
        return new com.digishield.learning.domain.CompliancePolicy(
                UUID.randomUUID(), TENANT_ID, "Policy", "GDPR", "before_due:7d", true, courseId);
    }
}
