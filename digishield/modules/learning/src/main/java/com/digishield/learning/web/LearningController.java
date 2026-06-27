package com.digishield.learning.web;

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
import com.digishield.shared.tenantcontext.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for the Learning module: catalog, enrollments, lessons,
 * quizzes/assessments, certificates, gamification and compliance.
 */
@RestController
class LearningController {

    private final LearningService learningService;

    LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    // ---- Catalog & enrollments --------------------------------------------

    @GetMapping("/api/v1/courses")
    ResponseEntity<java.util.List<CourseView>> courses() {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(learningService.listCourses(tenantId));
    }

    @GetMapping("/api/v1/enrollments")
    ResponseEntity<java.util.List<EnrollmentView>> enrollments(
            @RequestParam(value = "status", required = false) String status) {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(learningService.listEnrollments(tenantId, status));
    }

    @PostMapping("/api/v1/enrollments")
    ResponseEntity<EnrollmentView> enroll(@RequestBody EnrollRequest request) {
        UUID tenantId = TenantContext.requireUuid();
        EnrollmentView created = learningService.assign(tenantId, request.userId(), request.courseId());
        return ResponseEntity
                .created(URI.create("/api/v1/enrollments/" + created.id()))
                .body(created);
    }

    @PatchMapping("/api/v1/enrollments/{id}")
    ResponseEntity<EnrollmentView> updateEnrollment(@PathVariable UUID id,
                                                    @RequestBody UpdateEnrollmentRequest request) {
        UUID tenantId = TenantContext.requireUuid();
        int progress = request.progress() != null ? request.progress() : 0;
        return ResponseEntity.ok(learningService.updateProgress(tenantId, id, progress));
    }

    // ---- Lessons & quizzes -------------------------------------------------

    @GetMapping("/api/v1/lessons/{id}")
    ResponseEntity<LessonView> lesson(@PathVariable UUID id) {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(learningService.getLesson(tenantId, id));
    }

    @GetMapping("/api/v1/lessons/{id}/quiz")
    ResponseEntity<QuizView> quiz(@PathVariable UUID id) {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(learningService.getQuiz(tenantId, id));
    }

    /**
     * Submits quiz answers and returns the score (Quiz Results). The path id is
     * the lesson whose quiz was answered (assessment == lesson quiz here).
     */
    @PostMapping("/api/v1/assessments/{id}/responses")
    ResponseEntity<AssessmentResultView> submitResponses(@PathVariable UUID id,
                                                         @RequestBody SubmitResponsesRequest request) {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(learningService.submitResponses(tenantId, id, request.answers()));
    }

    // ---- Certificates ------------------------------------------------------

    @GetMapping("/api/v1/certificates/{id}")
    ResponseEntity<CertificateView> certificate(@PathVariable UUID id) {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(learningService.getCertificate(tenantId, id));
    }

    // ---- Gamification ------------------------------------------------------

    @GetMapping("/api/v1/gamification/leaderboard")
    ResponseEntity<java.util.List<LeaderboardRowView>> leaderboard() {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(learningService.getLeaderboard(tenantId));
    }

    @GetMapping("/api/v1/users/{id}/badges")
    ResponseEntity<java.util.List<BadgeView>> badges(@PathVariable UUID id) {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(learningService.getBadges(tenantId, id));
    }

    @GetMapping("/api/v1/users/{id}/points")
    ResponseEntity<Map<String, Object>> points(@PathVariable UUID id) {
        UUID tenantId = TenantContext.requireUuid();
        int total = learningService.getPoints(tenantId, id);
        return ResponseEntity.ok(Map.of("total", total, "entries", java.util.List.of()));
    }

    // ---- Compliance --------------------------------------------------------

    @GetMapping("/api/v1/compliance/policies")
    ResponseEntity<java.util.List<CompliancePolicyView>> compliancePolicies() {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(learningService.listCompliancePolicies(tenantId));
    }

    @GetMapping("/api/v1/compliance/status")
    ResponseEntity<ComplianceStatusView> complianceStatus() {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(learningService.getComplianceStatus(tenantId));
    }

    // ---- Request payloads --------------------------------------------------

    /** Payload for the enrollment creation request. */
    record EnrollRequest(UUID userId, UUID courseId) {
    }

    /** Payload for updating an enrollment's progress. */
    record UpdateEnrollmentRequest(Integer progress) {
    }

    /** Payload for submitting quiz answers (questionId/key -&gt; selected option index). */
    record SubmitResponsesRequest(Map<String, Integer> answers) {
    }
}
