package com.digishield.learning.application;

import com.digishield.contracts.events.EnrollmentAssignedEvent;
import com.digishield.learning.api.AssessmentResultView;
import com.digishield.learning.api.BadgeView;
import com.digishield.learning.api.CertificateView;
import com.digishield.learning.api.CompliancePolicyView;
import com.digishield.learning.api.ComplianceStatusView;
import com.digishield.learning.api.CourseView;
import com.digishield.learning.api.EnrollmentView;
import com.digishield.learning.api.LeaderboardRowView;
import com.digishield.learning.api.LearningService;
import com.digishield.learning.api.LessonView;
import com.digishield.learning.api.QuizView;
import com.digishield.learning.domain.Badge;
import com.digishield.learning.domain.Certificate;
import com.digishield.learning.domain.CompliancePolicy;
import com.digishield.learning.domain.Course;
import com.digishield.learning.domain.Enrollment;
import com.digishield.learning.domain.EnrollmentStatus;
import com.digishield.learning.domain.GamificationProfile;
import com.digishield.learning.domain.Lesson;
import com.digishield.learning.domain.QuizQuestion;
import com.digishield.learning.infrastructure.BadgeRepository;
import com.digishield.learning.infrastructure.CertificateRepository;
import com.digishield.learning.infrastructure.CompliancePolicyRepository;
import com.digishield.learning.infrastructure.CourseRepository;
import com.digishield.learning.infrastructure.EnrollmentRepository;
import com.digishield.learning.infrastructure.GamificationProfileRepository;
import com.digishield.learning.infrastructure.LessonRepository;
import com.digishield.learning.infrastructure.QuizQuestionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of {@link LearningService}.
 */
@Service
@Transactional
public class LearningServiceImpl implements LearningService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonRepository lessonRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final CertificateRepository certificateRepository;
    private final BadgeRepository badgeRepository;
    private final GamificationProfileRepository gamificationProfileRepository;
    private final CompliancePolicyRepository compliancePolicyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public LearningServiceImpl(CourseRepository courseRepository,
                               EnrollmentRepository enrollmentRepository,
                               LessonRepository lessonRepository,
                               QuizQuestionRepository quizQuestionRepository,
                               CertificateRepository certificateRepository,
                               BadgeRepository badgeRepository,
                               GamificationProfileRepository gamificationProfileRepository,
                               CompliancePolicyRepository compliancePolicyRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.lessonRepository = lessonRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.certificateRepository = certificateRepository;
        this.badgeRepository = badgeRepository;
        this.gamificationProfileRepository = gamificationProfileRepository;
        this.compliancePolicyRepository = compliancePolicyRepository;
        this.eventPublisher = eventPublisher;
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
        enrollment.setProgress(clamped);
        if (clamped >= 100) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
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
                .orElseGet(() -> enrollmentRepository.save(new Enrollment(
                        UUID.randomUUID(),
                        tenantId,
                        userId,
                        courseId,
                        EnrollmentStatus.ASSIGNED,
                        null
                )));

        eventPublisher.publishEvent(new EnrollmentAssignedEvent(tenantId, userId, courseId));

        return toEnrollmentView(enrollment, null);
    }

    @Override
    public EnrollmentView autoEnroll(UUID tenantId, UUID userId) {
        Course course = courseRepository.findFirstByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy khoá học nào cho tenant: " + tenantId));
        return assign(tenantId, userId, course.getId());
    }

    @Override
    public EnrollmentView completeQuiz(UUID tenantId, UUID enrollmentId, int score) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy lượt ghi danh: " + enrollmentId));
        enrollment.setScore(score);
        enrollment.setProgress(100);
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        return toEnrollmentView(enrollment, null);
    }

    @Override
    @Transactional(readOnly = true)
    public LessonView getLesson(UUID tenantId, UUID lessonId) {
        Lesson lesson = lessonRepository.findByTenantIdAndId(tenantId, lessonId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài học: " + lessonId));
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
    public int getPoints(UUID tenantId, UUID userId) {
        return gamificationProfileRepository.findByTenantIdAndUserId(tenantId, userId)
                .map(GamificationProfile::getPoints)
                .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompliancePolicyView> listCompliancePolicies(UUID tenantId) {
        return compliancePolicyRepository.findByTenantId(tenantId).stream()
                .map(this::toCompliancePolicyView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ComplianceStatusView getComplianceStatus(UUID tenantId) {
        List<CompliancePolicy> policies = compliancePolicyRepository.findByTenantId(tenantId);
        if (policies.isEmpty()) {
            return new ComplianceStatusView(0d, 0, 0, 0, 0, 0, 0);
        }
        double avg = policies.stream().mapToInt(CompliancePolicy::getCompletionPct).average().orElse(0d);
        int completed = (int) policies.stream().filter(p -> p.getCompletionPct() >= 90).count();
        int dueSoon = (int) policies.stream()
                .filter(p -> p.getCompletionPct() >= 50 && p.getCompletionPct() < 90).count();
        int total = 1240; // demo headcount used by the Compliance KPI tiles
        int compliantCount = (int) Math.round(avg / 100d * total);
        int overdue = total - compliantCount;
        return new ComplianceStatusView(
                Math.round(avg * 10d) / 10d, compliantCount, total, overdue,
                policies.size(), completed, dueSoon);
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
                enrollment.getStatus() != null ? enrollment.getStatus().name() : null,
                enrollment.getProgress(),
                enrollment.getScore()
        );
    }

    private BadgeView toBadgeView(Badge b) {
        return new BadgeView(b.getId(), b.getName(), b.getDescription(),
                b.getIconRef(), b.isEarned(), b.getAwardedAt());
    }

    private CompliancePolicyView toCompliancePolicyView(CompliancePolicy p) {
        return new CompliancePolicyView(p.getId(), p.getName(), p.getFramework(),
                p.getDueRule(), p.isMandatory(), p.getCompletionPct());
    }
}
