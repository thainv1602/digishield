package com.digishield.learning.application;

import com.digishield.contracts.events.EnrollmentAssignedEvent;
import com.digishield.learning.api.AssessmentResultView;
import com.digishield.learning.api.AssessmentResultsView;
import com.digishield.learning.api.AssessmentView;
import com.digishield.learning.api.BadgeView;
import com.digishield.learning.api.BadgeCatalogView;
import com.digishield.learning.api.PointRuleView;
import com.digishield.learning.api.CertificateView;
import com.digishield.learning.api.CoachingPageView;
import com.digishield.learning.api.CompliancePolicyView;
import com.digishield.learning.api.ComplianceStatusView;
import com.digishield.learning.api.CourseView;
import com.digishield.learning.api.EnrollmentView;
import com.digishield.learning.api.PassMarkProvider;
import com.digishield.learning.api.LeaderboardRowView;
import com.digishield.learning.api.LearningService;
import com.digishield.learning.api.LessonSummaryView;
import com.digishield.learning.api.LessonView;
import com.digishield.learning.api.PlacementResultView;
import com.digishield.learning.api.QuizView;
import com.digishield.learning.api.UserCertificateView;
import com.digishield.learning.domain.Assessment;
import com.digishield.learning.domain.AssessmentType;
import com.digishield.learning.domain.Badge;
import com.digishield.learning.domain.BadgeCatalog;
import com.digishield.learning.domain.BadgeCriteriaType;
import com.digishield.learning.domain.PointAction;
import com.digishield.learning.domain.PointRule;
import com.digishield.learning.domain.Certificate;
import com.digishield.learning.domain.CoachingPage;
import com.digishield.learning.domain.CompliancePolicy;
import com.digishield.learning.domain.Course;
import com.digishield.learning.domain.CourseLevel;
import com.digishield.learning.domain.Enrollment;
import com.digishield.learning.domain.EnrollmentStatus;
import com.digishield.learning.domain.GamificationProfile;
import com.digishield.learning.domain.Lesson;
import com.digishield.learning.domain.QuizQuestion;
import com.digishield.learning.infrastructure.AssessmentRepository;
import com.digishield.learning.infrastructure.BadgeRepository;
import com.digishield.learning.infrastructure.BadgeCatalogRepository;
import com.digishield.learning.infrastructure.CertificateRepository;
import com.digishield.learning.infrastructure.CoachingPageRepository;
import com.digishield.learning.infrastructure.CompliancePolicyRepository;
import com.digishield.learning.infrastructure.CourseRepository;
import com.digishield.learning.infrastructure.EnrollmentRepository;
import com.digishield.learning.infrastructure.GamificationProfileRepository;
import com.digishield.learning.infrastructure.LessonRepository;
import com.digishield.learning.infrastructure.PointRuleRepository;
import com.digishield.learning.infrastructure.QuizQuestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import com.digishield.learning.api.BehaviourHistory;
import java.util.Comparator;
import com.digishield.learning.api.LearnerDirectory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link LearningService}.
 */
@Service
@Transactional
public class LearningServiceImpl implements LearningService {

    /** Days a learner gets to finish remediation before it counts as late. */
    private static final int DEFAULT_REMEDIATION_DUE_DAYS = 3;

    /**
     * How long a learner has, from the property; a tenant that wants longer
     * changes configuration rather than code.
     */
    private final int remediationDueDays;

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonRepository lessonRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final CertificateRepository certificateRepository;
    private final BadgeRepository badgeRepository;
    private final BadgeCatalogRepository badgeCatalogRepository;
    private final GamificationProfileRepository gamificationProfileRepository;
    private final PointsAwarder pointsAwarder;
    private final LearnerDirectory learnerDirectory;
    private final String verifyUrl;
    /**
     * Both are required, so a missing one stops start-up rather than quietly
     * falling back — a pass mark or an offence count that silently reverts to a
     * default is the kind of fault this module has already shipped once.
     */
    private final PassMarkProvider passMarkProvider;
    private final BehaviourHistory behaviourHistory;
    private final CompliancePolicyRepository compliancePolicyRepository;
    private final AssessmentRepository assessmentRepository;
    private final CoachingPageRepository coachingPageRepository;
    private final PointRuleRepository pointRuleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public LearningServiceImpl(CourseRepository courseRepository,
                               EnrollmentRepository enrollmentRepository,
                               LessonRepository lessonRepository,
                               QuizQuestionRepository quizQuestionRepository,
                               CertificateRepository certificateRepository,
                               BadgeRepository badgeRepository,
                               BadgeCatalogRepository badgeCatalogRepository,
                               GamificationProfileRepository gamificationProfileRepository,
                               PointsAwarder pointsAwarder,
                               LearnerDirectory learnerDirectory,
                               @org.springframework.beans.factory.annotation.Value("${digishield.learning.certificate-verify-url:}") String verifyUrl,
                               PassMarkProvider passMarkProvider,
                               // Resolved lazily to break a start-up cycle:
                               // this reaches analytics, whose dashboard metrics
                               // reach back into learning.
                               @Lazy BehaviourHistory behaviourHistory,
                               CompliancePolicyRepository compliancePolicyRepository,
                               AssessmentRepository assessmentRepository,
                               CoachingPageRepository coachingPageRepository,
                               PointRuleRepository pointRuleRepository,
                               ApplicationEventPublisher eventPublisher,
                               ObjectMapper objectMapper,
                               @org.springframework.beans.factory.annotation.Value("${digishield.learning.remediation.due-days:3}") Integer remediationDueDays) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.lessonRepository = lessonRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.certificateRepository = certificateRepository;
        this.badgeRepository = badgeRepository;
        this.badgeCatalogRepository = badgeCatalogRepository;
        this.gamificationProfileRepository = gamificationProfileRepository;
        this.pointsAwarder = pointsAwarder;
        this.learnerDirectory = learnerDirectory;
        this.verifyUrl = verifyUrl == null || verifyUrl.isBlank() ? null : verifyUrl;
        this.passMarkProvider = passMarkProvider;
        this.behaviourHistory = behaviourHistory;
        this.compliancePolicyRepository = compliancePolicyRepository;
        this.assessmentRepository = assessmentRepository;
        this.coachingPageRepository = coachingPageRepository;
        this.pointRuleRepository = pointRuleRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        // Boxed, and defaulted here rather than only in the property: Mockito
        // constructs this class for the unit tests and cannot supply a primitive,
        // so it passes null. At runtime the property always resolves.
        this.remediationDueDays = remediationDueDays != null ? remediationDueDays : DEFAULT_REMEDIATION_DUE_DAYS;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseView> listCourses(UUID tenantId) {
        List<Course> courses = courseRepository.findByTenantIdOrderBySortOrderAsc(tenantId);
        if (courses.isEmpty()) {
            courses = courseRepository.findByTenantId(tenantId);
        }
        // Build a course -> latest enrollment map for derived progress/status.
        Map<UUID, Enrollment> byCourse = new java.util.HashMap<>();
        for (Enrollment e : enrollmentRepository.findByTenantId(tenantId)) {
            byCourse.put(e.getCourseId(), e);
        }

        List<CourseView> views = new ArrayList<>();
        boolean previousCompleted = true; // first course is always unlocked
        for (Course course : courses) {
            Enrollment enr = byCourse.get(course.getId());
            String status;
            Integer progress;
            if (enr != null) {
                progress = enr.getProgress() != null ? enr.getProgress()
                        : (enr.getStatus() == EnrollmentStatus.COMPLETED ? 100 : 0);
                status = switch (enr.getStatus()) {
                    case COMPLETED -> "completed";
                    case IN_PROGRESS, ASSIGNED, OVERDUE -> "in_progress";
                };
            } else if (previousCompleted) {
                progress = 0;
                status = "in_progress";
            } else {
                progress = 0;
                status = "locked";
            }
            previousCompleted = "completed".equals(status);
            views.add(toCourseView(course, progress, status));
        }
        return views;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentView> listEnrollments(UUID tenantId, String status) {
        List<Enrollment> enrollments;
        if (status != null && !status.isBlank()) {
            enrollments = enrollmentRepository.findByTenantIdAndStatus(
                    tenantId, EnrollmentStatus.valueOf(status.trim().toUpperCase()));
        } else {
            enrollments = enrollmentRepository.findByTenantId(tenantId);
        }
        Map<UUID, String> titles = new java.util.HashMap<>();
        for (Course c : courseRepository.findByTenantId(tenantId)) {
            titles.put(c.getId(), c.getTitle());
        }
        return enrollments.stream()
                .map(e -> toEnrollmentView(e, titles.get(e.getCourseId())))
                .toList();
    }

    @Override
    public EnrollmentView updateProgress(UUID tenantId, UUID enrollmentId, int progress) {
        Enrollment enrollment = enrollmentRepository.findByTenantIdAndId(tenantId, enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy lượt ghi danh: " + enrollmentId));
        int clamped = Math.max(0, Math.min(100, progress));
        boolean alreadyDone = enrollment.getStatus() == EnrollmentStatus.COMPLETED;
        enrollment.setProgress(clamped);
        if (clamped >= 100) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            // Reporting 100% repeatedly is normal client behaviour; paying for
            // it every time is not.
            if (!alreadyDone) {
                pointsAwarder.award(tenantId, enrollment.getUserId(), PointAction.LESSON_COMPLETED);
            }
        } else if (clamped > 0) {
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        }
        String title = courseRepository.findByTenantIdAndId(tenantId, enrollment.getCourseId())
                .map(Course::getTitle).orElse(null);
        return toEnrollmentView(enrollment, title);
    }

    @Override
    public EnrollmentView assign(UUID tenantId, UUID userId, UUID courseId) {
        Enrollment enrollment = enrollmentRepository
                .findByTenantIdAndUserIdAndCourseId(tenantId, userId, courseId)
                .orElseGet(() -> {
                    Enrollment created = new Enrollment(
                            UUID.randomUUID(),
                            tenantId,
                            userId,
                            courseId,
                            EnrollmentStatus.ASSIGNED,
                            null);
                    created.setDueAt(Instant.now().plus(remediationDueDays, ChronoUnit.DAYS));
                    return enrollmentRepository.save(created);
                });

        // Being assigned a course already finished means it is being assigned
        // again — a repeat offence. This used to return the old completed record
        // untouched, so a second click produced no training at all and the
        // person stayed marked compliant on the strength of the first time.
        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) {
            enrollment.setStatus(EnrollmentStatus.ASSIGNED);
            enrollment.setProgress(0);
            enrollment.setScore(null);
            // A repeat offence starts its own clock. Keeping the old date would
            // hand someone a deadline that expired before they were told about it.
            enrollment.setDueAt(Instant.now().plus(remediationDueDays, ChronoUnit.DAYS));
            enrollment = enrollmentRepository.save(enrollment);
        }

        eventPublisher.publishEvent(new EnrollmentAssignedEvent(tenantId, userId, courseId));

        return toEnrollmentView(enrollment, null);
    }

    @Override
    public EnrollmentView autoEnroll(UUID tenantId, UUID userId) {
        List<Course> catalogue = courseRepository.findByTenantIdOrderBySortOrderAsc(tenantId);
        if (catalogue.isEmpty()) {
            catalogue = courseRepository.findByTenantId(tenantId);
        }
        if (catalogue.isEmpty()) {
            throw new IllegalStateException("Không tìm thấy khoá học nào cho tenant: " + tenantId);
        }
        Course course = remediationFor(tenantId, userId, catalogue);
        return assign(tenantId, userId, course.getId());
    }

    /**
     * Picks remediation to match how often this has happened before.
     * <p>
     * Previously this took whatever course came back first, so someone on their
     * fifth click was sent the same introduction as someone on their first. Each
     * repeat now moves one step up the catalogue's levels, stopping at the
     * hardest the tenant has — the ladder cannot go further than the courses
     * that exist.
     */
    private Course remediationFor(UUID tenantId, UUID userId, List<Course> catalogue) {
        List<Course> byLevel = catalogue.stream()
                .sorted(Comparator
                        .comparingInt((Course c) -> c.getLevel() == null ? 0 : c.getLevel().rank())
                        .thenComparing(c -> c.getSortOrder() == null ? 0 : c.getSortOrder()))
                .toList();

        // The click that triggered this is already recorded, so one click means
        // a first offence and step 0 of the ladder.
        int clicks = clickCount(tenantId, userId);
        int step = Math.max(0, clicks - 1);

        List<Integer> levels = byLevel.stream()
                .map(c -> c.getLevel() == null ? 0 : c.getLevel().rank())
                .distinct()
                .toList();
        int targetLevel = levels.get(Math.min(step, levels.size() - 1));

        return byLevel.stream()
                .filter(c -> (c.getLevel() == null ? 0 : c.getLevel().rank()) == targetLevel)
                .findFirst()
                .orElse(byLevel.get(0));
    }

    private int clickCount(UUID tenantId, UUID userId) {
        // The click that triggered this is already recorded, so the count is at
        // least one; a zero would read as "never happened" and pick the gentlest
        // course for someone who just failed.
        return Math.max(1, behaviourHistory.simulationClicks(tenantId, userId));
    }

    @Override
    public EnrollmentView completeQuiz(UUID tenantId, UUID enrollmentId, int score) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy lượt ghi danh: " + enrollmentId));
        boolean alreadyDone = enrollment.getStatus() == EnrollmentStatus.COMPLETED;
        enrollment.setScore(score);

        if (score < passMark(tenantId)) {
            // Failing is not finishing. This used to mark the course COMPLETED
            // whatever the score, so someone who scored 10 was recorded as
            // trained, counted as fully compliant, and — once points existed —
            // paid for it. They keep their place and sit it again.
            if (!alreadyDone) {
                enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            }
            return toEnrollmentView(enrollment, null);
        }

        enrollment.setProgress(100);
        enrollment.setStatus(EnrollmentStatus.COMPLETED);

        // Only on the transition: re-sitting a course already passed must not
        // pay again.
        if (!alreadyDone) {
            pointsAwarder.award(tenantId, enrollment.getUserId(), PointAction.LESSON_COMPLETED);
            pointsAwarder.award(tenantId, enrollment.getUserId(), PointAction.QUIZ_PASSED);
            issueCertificate(tenantId, enrollment, score);
        }
        return toEnrollmentView(enrollment, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LessonSummaryView> listLessons(UUID tenantId) {
        Map<UUID, String> courseTitles = new java.util.HashMap<>();
        for (Course c : courseRepository.findByTenantId(tenantId)) {
            courseTitles.put(c.getId(), c.getTitle());
        }
        List<LessonSummaryView> views = new ArrayList<>();
        for (Lesson lesson : lessonRepository.findByTenantIdOrderBySortOrderAsc(tenantId)) {
            int questionCount = quizQuestionRepository
                    .findByTenantIdAndLessonIdOrderBySortOrderAsc(tenantId, lesson.getId())
                    .size();
            views.add(new LessonSummaryView(
                    lesson.getId(),
                    lesson.getCourseId(),
                    courseTitles.get(lesson.getCourseId()),
                    lesson.getTitle(),
                    lesson.getDurationMin(),
                    questionCount));
        }
        return views;
    }

    @Override
    @Transactional(readOnly = true)
    public LessonView getLesson(UUID tenantId, UUID lessonId) {
        Lesson lesson = lessonRepository.findByTenantIdAndId(tenantId, lessonId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài học: " + lessonId));
        return toLessonView(lesson);
    }

    private LessonView toLessonView(Lesson lesson) {
        List<LessonView.CheckpointView> checkpoints = new ArrayList<>();
        if (lesson.getCheckpoints() != null && !lesson.getCheckpoints().isBlank()) {
            String[] labels = lesson.getCheckpoints().split("\\s*,\\s*");
            // Mark the first as done, the second as current, rest as todo (demo outline).
            for (int i = 0; i < labels.length; i++) {
                String state = i == 0 ? "done" : i == 1 ? "current" : "todo";
                checkpoints.add(new LessonView.CheckpointView(labels[i], state));
            }
        }
        return new LessonView(
                lesson.getId(),
                lesson.getCourseId(),
                lesson.getTitle(),
                lesson.getBody(),
                lesson.getExampleTitle(),
                lesson.getExampleBody(),
                lesson.getClosing(),
                lesson.getDurationMin(),
                checkpoints.isEmpty() ? 0 : Math.round(100f / checkpoints.size()),
                checkpoints
        );
    }

    @Override
    @Transactional(readOnly = true)
    public QuizView getQuiz(UUID tenantId, UUID lessonId) {
        List<QuizView.QuizQuestionView> questions = quizQuestionRepository
                .findByTenantIdAndLessonIdOrderBySortOrderAsc(tenantId, lessonId).stream()
                .map(q -> new QuizView.QuizQuestionView(
                        q.getId(),
                        q.getPrompt(),
                        List.of(q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD()),
                        q.getCorrectIndex(),
                        q.getExplanation()))
                .toList();
        return new QuizView(lessonId, questions);
    }

    @Override
    public AssessmentResultView submitResponses(UUID tenantId, UUID lessonId,
                                                Map<String, Integer> answers) {
        List<QuizQuestion> questions = quizQuestionRepository
                .findByTenantIdAndLessonIdOrderBySortOrderAsc(tenantId, lessonId);
        int score = 0;
        int num = 1;
        List<AssessmentResultView.ReviewRow> review = new ArrayList<>();
        for (QuizQuestion q : questions) {
            Integer selected = answers != null
                    ? answers.getOrDefault(q.getId().toString(), answers.get("q" + num))
                    : null;
            boolean correct = selected != null && selected == q.getCorrectIndex();
            if (correct) {
                score++;
            }
            review.add(new AssessmentResultView.ReviewRow(
                    num, correct, correct ? "" : q.getExplanation()));
            num++;
        }
        int total = questions.size();
        boolean passed = total > 0 && (double) score / total >= 0.7;
        return new AssessmentResultView(score, total, passed, review);
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateView getCertificate(UUID tenantId, UUID certificateId) {
        Certificate c = certificateRepository.findByTenantIdAndId(tenantId, certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy chứng chỉ: " + certificateId));
        String qr = c.getVerifyUrl() != null
                ? c.getVerifyUrl() + "?serial=" + c.getSerial()
                : c.getSerial();
        return new CertificateView(
                c.getId(), c.getSerial(), c.getCourseTitle(), c.getRecipient(),
                c.getScore(), c.getIssuedAt(), c.getValidUntil(), c.getVerifyUrl(), qr);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserCertificateView> listCertificates(UUID tenantId, UUID userId) {
        return certificateRepository.findByTenantIdAndUserId(tenantId, userId).stream()
                .map(this::toUserCertificateView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentView> listAssessments(UUID tenantId, String type) {
        List<Assessment> assessments;
        if (type != null && !type.isBlank()) {
            assessments = assessmentRepository.findByTenantIdAndType(
                    tenantId, AssessmentType.valueOf(type.trim().toUpperCase()));
        } else {
            assessments = assessmentRepository.findByTenantId(tenantId);
        }
        return assessments.stream().map(this::toAssessmentView).toList();
    }

    @Override
    public AssessmentView createAssessment(UUID tenantId, AssessmentView request) {
        AssessmentType type = request.type() != null
                ? AssessmentType.valueOf(request.type().trim().toUpperCase())
                : AssessmentType.KNOWLEDGE;
        Assessment assessment = new Assessment(
                request.id() != null ? request.id() : UUID.randomUUID(),
                tenantId,
                type,
                request.anonymous(),
                writeJson(request.questionsJson()),
                request.period(),
                0);
        return toAssessmentView(assessmentRepository.save(assessment));
    }

    @Override
    @Transactional(readOnly = true)
    public AssessmentResultsView getAssessmentResults(UUID tenantId, UUID assessmentId) {
        Assessment assessment = assessmentRepository.findByTenantIdAndId(tenantId, assessmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy bài đánh giá: " + assessmentId));
        int count = assessment.getResponseCount();
        // Anonymized aggregate summary used by the results dashboard.
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("type", assessment.getType().name().toLowerCase());
        summary.put("anonymous", assessment.isAnonymous());
        summary.put("period", assessment.getPeriod());
        summary.put("completion_rate", count > 0 ? 100 : 0);
        return new AssessmentResultsView(count, summary);
    }

    @Override
    public PlacementResultView placement(UUID tenantId, UUID userId,
                                         Map<String, Object> answers) {
        int total = answers != null ? answers.size() : 0;
        int correct = 0;
        if (answers != null) {
            for (Object value : answers.values()) {
                if (isCorrectSignal(value)) {
                    correct++;
                }
            }
        }
        double ratio = total > 0 ? (double) correct / total : 0d;
        String level;
        if (ratio >= 0.9) {
            level = "advanced";
        } else if (ratio >= 0.7) {
            level = "intermediate";
        } else if (ratio >= 0.4) {
            level = "beginner";
        } else {
            level = "basic";
        }
        return new PlacementResultView(level);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CoachingPageView> listCoachingPages(UUID tenantId) {
        return coachingPageRepository.findByTenantId(tenantId).stream()
                .map(this::toCoachingPageView)
                .toList();
    }

    @Override
    public CoachingPageView createCoachingPage(UUID tenantId, CoachingPageView request) {
        CoachingPage page = new CoachingPage(
                request.id() != null ? request.id() : UUID.randomUUID(),
                tenantId,
                request.templateId(),
                request.contentRef(),
                writeJson(request.signalsJson()));
        return toCoachingPageView(coachingPageRepository.save(page));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaderboardRowView> getLeaderboard(UUID tenantId) {
        List<GamificationProfile> profiles =
                gamificationProfileRepository.findByTenantIdOrderByPointsDesc(tenantId);
        List<LeaderboardRowView> rows = new ArrayList<>();
        int rank = 1;
        for (GamificationProfile p : profiles) {
            rows.add(new LeaderboardRowView(rank++, p.getDisplayName(), p.getPoints()));
        }
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BadgeView> getBadges(UUID tenantId, UUID userId) {
        return badgeRepository.findByTenantIdAndUserId(tenantId, userId).stream()
                .map(this::toBadgeView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BadgeCatalogView> listBadgeCatalog(UUID tenantId) {
        return badgeCatalogRepository.findByTenantIdOrderByName(tenantId).stream()
                .map(LearningServiceImpl::toBadgeCatalogView)
                .toList();
    }

    @Override
    public BadgeCatalogView createBadge(UUID tenantId, BadgeCatalogView command) {
        BadgeCatalog badge = new BadgeCatalog(
                UUID.randomUUID(), tenantId, command.name(), command.description(), command.iconRef(),
                criteriaType(command.criteriaType()), command.criteriaThreshold());
        return toBadgeCatalogView(badgeCatalogRepository.save(badge));
    }

    @Override
    public void deleteBadge(UUID tenantId, UUID id) {
        // RLS-scoped: findById only returns this tenant's rows.
        badgeCatalogRepository.findById(id)
                .filter(b -> b.getTenantId().equals(tenantId))
                .ifPresent(badgeCatalogRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public int getPoints(UUID tenantId, UUID userId) {
        return gamificationProfileRepository.findByTenantIdAndUserId(tenantId, userId)
                .map(GamificationProfile::getPoints)
                .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PointRuleView> listPointRules(UUID tenantId) {
        List<PointRuleView> configured = pointRuleRepository.findByTenantIdOrderByPointsDesc(tenantId)
                .stream()
                .map(r -> new PointRuleView(r.getAction(), r.getLabel(), r.getPoints()))
                .toList();
        if (!configured.isEmpty()) {
            return configured;
        }
        // A tenant with no rules of its own still earns points, on the built-in
        // defaults — so this has to show those, or the screen explaining how
        // scoring works would contradict the scoring.
        return java.util.Arrays.stream(PointAction.values())
                .sorted(java.util.Comparator.comparingInt(PointAction::defaultPoints).reversed())
                .map(a -> new PointRuleView(a.wireName(), a.label(), a.defaultPoints()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompliancePolicyView> listCompliancePolicies(UUID tenantId) {
        List<CompliancePolicy> policies = compliancePolicyRepository.findByTenantId(tenantId);
        CompletionIndex index = completionIndex(tenantId);
        return policies.stream()
                .map(p -> toCompliancePolicyView(p, index.pctFor(p.getCourseId())))
                .toList();
    }

    @Override
    public CompliancePolicyView createCompliancePolicy(UUID tenantId, String name, String framework,
                                                       String dueRule, boolean mandatory,
                                                       UUID courseId) {
        String resolvedName = name != null && !name.isBlank()
                ? name
                : (framework != null && !framework.isBlank() ? framework : "Chính sách tuân thủ");
        CompliancePolicy policy = new CompliancePolicy(
                UUID.randomUUID(), tenantId, resolvedName, framework, dueRule, mandatory, courseId);
        CompliancePolicy saved = compliancePolicyRepository.save(policy);
        return toCompliancePolicyView(saved, completionIndex(tenantId).pctFor(saved.getCourseId()));
    }

    @Override
    @Transactional(readOnly = true)
    public ComplianceStatusView getComplianceStatus(UUID tenantId) {
        List<CompliancePolicy> policies = compliancePolicyRepository.findByTenantId(tenantId);
        if (policies.isEmpty()) {
            return new ComplianceStatusView(0d, 0, 0, 0, 0, 0, 0);
        }
        CompletionIndex index = completionIndex(tenantId);
        List<Integer> pcts = policies.stream()
                .map(p -> index.pctFor(p.getCourseId()))
                .toList();

        double avg = pcts.stream().mapToInt(Integer::intValue).average().orElse(0d);
        int completed = (int) pcts.stream().filter(pct -> pct >= 90).count();
        int dueSoon = (int) pcts.stream().filter(pct -> pct >= 50 && pct < 90).count();

        int total = (int) enrollmentRepository.countDistinctUsers(tenantId);
        // A person counts as compliant when they have nothing outstanding in any
        // course a policy points at — derived per person, not inferred from the
        // average, which used to spread a policy-level number over head count.
        int compliantCount = index.compliantUsers(policies);
        int overdue = Math.max(0, total - compliantCount);

        return new ComplianceStatusView(
                Math.round(avg * 10d) / 10d, compliantCount, total, overdue,
                policies.size(), completed, dueSoon);
    }

    /**
     * Enrollment facts for a tenant, loaded once so a page of N policies costs one
     * query rather than N.
     */
    private CompletionIndex completionIndex(UUID tenantId) {
        return new CompletionIndex(enrollmentRepository.findByTenantId(tenantId));
    }

    /** Completion percentages derived from a tenant's enrollments. */
    private static final class CompletionIndex {

        private final List<Enrollment> enrollments;
        private final Map<UUID, int[]> byCourse = new HashMap<>();
        private final int overallPct;

        private CompletionIndex(List<Enrollment> enrollments) {
            this.enrollments = enrollments;
            int done = 0;
            for (Enrollment e : enrollments) {
                int[] counts = byCourse.computeIfAbsent(e.getCourseId(), k -> new int[2]);
                counts[1]++;
                if (e.getStatus() == EnrollmentStatus.COMPLETED) {
                    counts[0]++;
                    done++;
                }
            }
            this.overallPct = enrollments.isEmpty()
                    ? 0
                    : (int) Math.round(done * 100d / enrollments.size());
        }

        /**
         * Completion of the course a policy points at. A policy with no course
         * (a broad framework) reports the tenant's overall training completion
         * rather than dropping out of the report.
         */
        int pctFor(UUID courseId) {
            if (courseId == null) {
                return overallPct;
            }
            int[] counts = byCourse.get(courseId);
            if (counts == null || counts[1] == 0) {
                return 0;
            }
            return (int) Math.round(counts[0] * 100d / counts[1]);
        }

        /** People with no unfinished enrollment in any policy-covered course. */
        int compliantUsers(List<CompliancePolicy> policies) {
            Set<UUID> covered = policies.stream()
                    .map(CompliancePolicy::getCourseId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<UUID> everyone = new HashSet<>();
            Set<UUID> outstanding = new HashSet<>();
            for (Enrollment e : enrollments) {
                everyone.add(e.getUserId());
                boolean relevant = covered.isEmpty() || covered.contains(e.getCourseId());
                if (relevant && e.getStatus() != EnrollmentStatus.COMPLETED) {
                    outstanding.add(e.getUserId());
                }
            }
            everyone.removeAll(outstanding);
            return everyone.size();
        }
    }

    // ---- mappers -----------------------------------------------------------

    private CourseView toCourseView(Course course, Integer progress, String status) {
        return new CourseView(
                course.getId(),
                course.getTenantId(),
                course.getTitle(),
                course.getLevel() != null ? course.getLevel().name().toLowerCase() : null,
                course.getLang(),
                course.getDurationMin(),
                course.getLessonCount(),
                progress,
                status
        );
    }

    private EnrollmentView toEnrollmentView(Enrollment enrollment, String courseTitle) {
        return new EnrollmentView(
                enrollment.getId(),
                enrollment.getTenantId(),
                enrollment.getUserId(),
                enrollment.getCourseId(),
                courseTitle,
                statusOf(enrollment),
                enrollment.getProgress(),
                enrollment.getScore(),
                enrollment.getDueAt()
        );
    }

    /**
     * The status a caller should see, which is not always the one stored.
     *
     * <p>An assignment is late from the moment its deadline passes, not from the
     * moment a sweep gets round to writing OVERDUE. Reporting the stored value
     * would show ASSIGNED to someone who is already late, for as long as the job
     * took to run.
     */
    private String statusOf(Enrollment enrollment) {
        if (enrollment.getStatus() == null) {
            return null;
        }
        if (enrollment.isOverdue(Instant.now())) {
            return EnrollmentStatus.OVERDUE.name();
        }
        return enrollment.getStatus().name();
    }

    private BadgeView toBadgeView(Badge b) {
        return new BadgeView(b.getId(), b.getName(), b.getDescription(),
                b.getIconRef(), b.isEarned(), b.getAwardedAt());
    }

    private CompliancePolicyView toCompliancePolicyView(CompliancePolicy p, int completionPct) {
        return new CompliancePolicyView(p.getId(), p.getName(), p.getFramework(),
                p.getDueRule(), p.isMandatory(), completionPct);
    }

    private UserCertificateView toUserCertificateView(Certificate c) {
        return new UserCertificateView(
                c.getId(), c.getUserId(), c.getCourseId(), c.getSerial(),
                c.getVerifyUrl(), c.getIssuedAt());
    }

    private AssessmentView toAssessmentView(Assessment a) {
        return new AssessmentView(
                a.getId(),
                a.getType() != null ? a.getType().name().toLowerCase() : null,
                a.isAnonymous(),
                readJson(a.getQuestionsJson()),
                a.getPeriod());
    }

    private CoachingPageView toCoachingPageView(CoachingPage p) {
        return new CoachingPageView(
                p.getId(), p.getTemplateId(), p.getContentRef(), readJson(p.getSignalsJson()));
    }

    private static boolean isCorrectSignal(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() > 0;
        }
        if (value instanceof String s) {
            return "correct".equalsIgnoreCase(s.trim()) || "true".equalsIgnoreCase(s.trim());
        }
        return false;
    }

    /** Parses a stored JSON document into a generic object (empty map on failure). */
    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** Serializes a generic object into a JSON document (null when empty). */

    @Override
    @Transactional
    public CourseView createCourse(UUID tenantId, CourseView request) {
        Course course = new Course(
                request.id() != null ? request.id() : UUID.randomUUID(),
                tenantId,
                requireTitle(request.title()),
                level(request.level()),
                request.lang() != null ? request.lang() : "vi",
                request.durationMin(),
                // Derived, never taken from the caller: a course starts with no
                // lessons, and the count is maintained as they are added.
                0,
                nextCourseSortOrder(tenantId));
        return toCourseView(courseRepository.save(course), null, null);
    }

    @Override
    @Transactional
    public CourseView updateCourse(UUID tenantId, UUID courseId, CourseView request) {
        Course course = courseRepository.findByTenantIdAndId(tenantId, courseId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khoá học: " + courseId));
        if (request.title() != null && !request.title().isBlank()) {
            course.setTitle(request.title().trim());
        }
        if (request.level() != null) {
            course.setLevel(level(request.level()));
        }
        if (request.lang() != null) {
            course.setLang(request.lang());
        }
        if (request.durationMin() != null) {
            course.setDurationMin(request.durationMin());
        }
        // lessonCount is deliberately not writable: it is a fact about the
        // lessons, and letting it be set by hand is how it starts disagreeing
        // with them.
        return toCourseView(courseRepository.save(course), null, null);
    }

    @Override
    @Transactional
    public void deleteCourse(UUID tenantId, UUID courseId) {
        Course course = courseRepository.findByTenantIdAndId(tenantId, courseId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khoá học: " + courseId));
        if (enrollmentRepository.existsByTenantIdAndCourseId(tenantId, courseId)) {
            throw new IllegalStateException(
                    "Không thể xoá khoá học đang có người học: " + courseId);
        }
        lessonRepository.deleteByTenantIdAndCourseId(tenantId, courseId);
        courseRepository.delete(course);
    }

    @Override
    @Transactional
    public LessonView createLesson(UUID tenantId, LessonView request) {
        UUID courseId = request.courseId();
        Course course = courseRepository.findByTenantIdAndId(tenantId, courseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy khoá học cho bài học: " + courseId));
        Lesson lesson = new Lesson(
                request.id() != null ? request.id() : UUID.randomUUID(),
                tenantId,
                courseId,
                requireTitle(request.title()),
                request.body(),
                request.exampleTitle(),
                request.example(),
                request.closing(),
                checkpointLabels(request.checkpoints()),
                request.durationMin(),
                lessonRepository.countByTenantIdAndCourseId(tenantId, courseId) + 1);
        Lesson saved = lessonRepository.save(lesson);
        refreshLessonCount(tenantId, course);
        return toLessonView(saved);
    }

    @Override
    @Transactional
    public LessonView updateLesson(UUID tenantId, UUID lessonId, LessonView request) {
        Lesson lesson = lessonRepository.findByTenantIdAndId(tenantId, lessonId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài học: " + lessonId));
        if (request.title() != null && !request.title().isBlank()) {
            lesson.setTitle(request.title().trim());
        }
        if (request.body() != null) {
            lesson.setBody(request.body());
        }
        if (request.exampleTitle() != null) {
            lesson.setExampleTitle(request.exampleTitle());
        }
        if (request.example() != null) {
            lesson.setExampleBody(request.example());
        }
        if (request.closing() != null) {
            lesson.setClosing(request.closing());
        }
        if (request.durationMin() != null) {
            lesson.setDurationMin(request.durationMin());
        }
        if (request.checkpoints() != null && !request.checkpoints().isEmpty()) {
            lesson.setCheckpoints(checkpointLabels(request.checkpoints()));
        }
        return toLessonView(lessonRepository.save(lesson));
    }

    @Override
    @Transactional
    public void deleteLesson(UUID tenantId, UUID lessonId) {
        Lesson lesson = lessonRepository.findByTenantIdAndId(tenantId, lessonId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài học: " + lessonId));
        UUID courseId = lesson.getCourseId();
        lessonRepository.delete(lesson);
        courseRepository.findByTenantIdAndId(tenantId, courseId)
                .ifPresent(course -> refreshLessonCount(tenantId, course));
    }

    /** Rewrites the stored count from the lessons that actually exist. */
    private void refreshLessonCount(UUID tenantId, Course course) {
        course.setLessonCount(lessonRepository.countByTenantIdAndCourseId(tenantId, course.getId()));
        courseRepository.save(course);
    }

    private int nextCourseSortOrder(UUID tenantId) {
        return courseRepository.findByTenantId(tenantId).stream()
                .map(Course::getSortOrder)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Tiêu đề không được để trống");
        }
        return title.trim();
    }

    /** Checkpoints are stored as a comma-separated list of labels. */
    private static String checkpointLabels(List<LessonView.CheckpointView> checkpoints) {
        if (checkpoints == null || checkpoints.isEmpty()) {
            return null;
        }
        return checkpoints.stream()
                .map(LessonView.CheckpointView::label)
                .filter(l -> l != null && !l.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static CourseLevel level(String wire) {
        if (wire == null || wire.isBlank()) {
            return CourseLevel.BASIC;
        }
        try {
            return CourseLevel.valueOf(wire.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cấp độ không hợp lệ: " + wire);
        }
    }


    /**
     * The tenant's configured pass score, or the platform default when no
     * provider is wired.
     */
    private int passMark(UUID tenantId) {
        return passMarkProvider.passScorePct(tenantId);
    }


    /**
     * Issues the certificate for a course just passed.
     * <p>
     * Nothing created these before: the table had one writer, a dev seeder, so
     * the certificates screen was empty for everyone who had actually earned
     * one. Issued only on the transition, so re-sitting a passed course does not
     * mint a second.
     */
    private void issueCertificate(UUID tenantId, Enrollment enrollment, int score) {
        UUID userId = enrollment.getUserId();
        if (certificateRepository.findByTenantIdAndUserId(tenantId, userId).stream()
                .anyMatch(c -> enrollment.getCourseId().equals(c.getCourseId()))) {
            return;
        }
        String courseTitle = courseRepository.findByTenantIdAndId(tenantId, enrollment.getCourseId())
                .map(Course::getTitle)
                .orElse(null);
        Instant issuedAt = Instant.now();
        certificateRepository.save(new Certificate(
                UUID.randomUUID(),
                tenantId,
                userId,
                enrollment.getCourseId(),
                serial(issuedAt),
                courseTitle,
                learnerName(userId),
                score,
                issuedAt,
                // Awareness training goes stale; a certificate that never
                // expires would let a tenant report someone as trained on a
                // course they took years ago.
                issuedAt.plus(java.time.Duration.ofDays(365)),
                verifyUrl));
    }

    /** Human-quotable, unique enough to look up, no personal data in it. */
    private static String serial(Instant issuedAt) {
        String year = issuedAt.toString().substring(0, 4);
        String random = UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT)
                .replace("-", "");
        return "DS-" + year + "-" + random.substring(0, 4) + "-" + random.substring(4, 8);
    }

    private String learnerName(UUID userId) {
        return learnerDirectory.find(userId)
                .map(LearnerDirectory.Learner::displayName)
                .orElse(null);
    }


    private static BadgeCatalogView toBadgeCatalogView(BadgeCatalog b) {
        return new BadgeCatalogView(b.getId(), b.getName(), b.getDescription(), b.getIconRef(),
                b.getCriteriaType() == null ? null : b.getCriteriaType().name(),
                b.getCriteriaThreshold());
    }

    /** Rejected at entry, so a badge cannot be defined against a measure nothing reads. */
    private static BadgeCriteriaType criteriaType(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        try {
            return BadgeCriteriaType.valueOf(wire.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tiêu chí không hợp lệ: " + wire, e);
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
