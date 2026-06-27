package com.digishield.learning.api;

import java.util.List;
import java.util.UUID;

/**
 * Public API of the Learning module.
 */
public interface LearningService {

    /**
     * Gets the catalog of courses for a tenant, with derived progress/status.
     */
    List<CourseView> listCourses(UUID tenantId);

    /**
     * Gets the enrollments of a tenant, optionally filtered by status.
     *
     * @param tenantId tenant
     * @param status   lower-case status filter (assigned|in_progress|completed|overdue),
     *                 or {@code null} for all
     */
    List<EnrollmentView> listEnrollments(UUID tenantId, String status);

    /**
     * Updates the progress (and derived status) of an enrollment.
     *
     * @param tenantId     tenant
     * @param enrollmentId enrollment identifier
     * @param progress     new progress percentage (0..100)
     * @return updated view
     */
    EnrollmentView updateProgress(UUID tenantId, UUID enrollmentId, int progress);

    /**
     * Assigns (enrolls) a user into a specific course.
     */
    EnrollmentView assign(UUID tenantId, UUID userId, UUID courseId);

    /**
     * Automatically selects a suitable course and assigns it to the user.
     */
    EnrollmentView autoEnroll(UUID tenantId, UUID userId);

    /**
     * Records the quiz result and updates the status/score of the enrollment.
     */
    EnrollmentView completeQuiz(UUID tenantId, UUID enrollmentId, int score);

    /**
     * Gets the lesson content + checkpoint outline for the Lesson Player.
     */
    LessonView getLesson(UUID tenantId, UUID lessonId);

    /**
     * Gets the quiz payload (questions A–D + correct answer) for a lesson.
     */
    QuizView getQuiz(UUID tenantId, UUID lessonId);

    /**
     * Scores submitted answers for a lesson's quiz (Quiz Results).
     *
     * @param tenantId tenant
     * @param lessonId lesson whose quiz was answered
     * @param answers  map of question id (string) -&gt; selected 0-based option index
     */
    AssessmentResultView submitResponses(UUID tenantId, UUID lessonId,
                                         java.util.Map<String, Integer> answers);

    /**
     * Gets a certificate by id.
     */
    CertificateView getCertificate(UUID tenantId, UUID certificateId);

    /**
     * Lists the certificates issued to a user.
     */
    List<UserCertificateView> listCertificates(UUID tenantId, UUID userId);

    /**
     * Lists the assessments of a tenant, optionally filtered by type.
     *
     * @param tenantId tenant
     * @param type     lower-case type filter (knowledge|culture|placement),
     *                 or {@code null} for all
     */
    List<AssessmentView> listAssessments(UUID tenantId, String type);

    /**
     * Creates a new assessment for a tenant.
     */
    AssessmentView createAssessment(UUID tenantId, AssessmentView request);

    /**
     * Gets the aggregated (anonymized) results of an assessment.
     */
    AssessmentResultsView getAssessmentResults(UUID tenantId, UUID assessmentId);

    /**
     * Runs an adaptive placement assessment and returns the recommended level.
     *
     * @param tenantId tenant
     * @param userId   user being placed
     * @param answers  map of question id -&gt; submitted answer value
     */
    PlacementResultView placement(UUID tenantId, UUID userId,
                                  java.util.Map<String, Object> answers);

    /**
     * Lists the just-in-time coaching pages of a tenant.
     */
    List<CoachingPageView> listCoachingPages(UUID tenantId);

    /**
     * Creates (or updates) a coaching page for a tenant.
     */
    CoachingPageView createCoachingPage(UUID tenantId, CoachingPageView request);

    /**
     * Gets the leaderboard rows for a tenant.
     */
    List<LeaderboardRowView> getLeaderboard(UUID tenantId);

    /**
     * Gets a user's badges.
     */
    List<BadgeView> getBadges(UUID tenantId, UUID userId);

    /**
     * Gets a user's total accumulated points.
     */
    int getPoints(UUID tenantId, UUID userId);

    /**
     * Lists the compliance policies of a tenant.
     */
    List<CompliancePolicyView> listCompliancePolicies(UUID tenantId);

    /**
     * Creates a compliance policy (mandatory training) for a tenant.
     *
     * @param tenantId      tenant
     * @param name          policy name (may be null; falls back to the framework)
     * @param framework     mapped compliance framework
     * @param dueRule       human-readable due rule / deadline text
     * @param mandatory     whether the policy is mandatory
     * @param completionPct initial completion percentage (0..100)
     */
    CompliancePolicyView createCompliancePolicy(UUID tenantId, String name, String framework,
                                                String dueRule, boolean mandatory,
                                                int completionPct);

    /**
     * Gets the aggregated compliance status of a tenant.
     */
    ComplianceStatusView getComplianceStatus(UUID tenantId);
}
