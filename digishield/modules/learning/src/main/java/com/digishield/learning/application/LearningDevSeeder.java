package com.digishield.learning.application;

import com.digishield.learning.domain.Assessment;
import com.digishield.learning.domain.AssessmentType;
import com.digishield.learning.domain.Badge;
import com.digishield.learning.domain.Certificate;
import com.digishield.learning.domain.CoachingPage;
import com.digishield.learning.domain.CompliancePolicy;
import com.digishield.learning.domain.Course;
import com.digishield.learning.domain.CourseLevel;
import com.digishield.learning.domain.Enrollment;
import com.digishield.learning.domain.EnrollmentStatus;
import com.digishield.learning.domain.GamificationProfile;
import com.digishield.learning.domain.Lesson;
import com.digishield.learning.domain.PointRule;
import com.digishield.learning.domain.QuizQuestion;
import com.digishield.learning.infrastructure.AssessmentRepository;
import com.digishield.learning.infrastructure.BadgeRepository;
import com.digishield.learning.infrastructure.CertificateRepository;
import com.digishield.learning.infrastructure.CoachingPageRepository;
import com.digishield.learning.infrastructure.CompliancePolicyRepository;
import com.digishield.learning.infrastructure.CourseRepository;
import com.digishield.learning.infrastructure.EnrollmentRepository;
import com.digishield.learning.infrastructure.GamificationProfileRepository;
import com.digishield.learning.infrastructure.LessonRepository;
import com.digishield.learning.infrastructure.PointRuleRepository;
import com.digishield.learning.infrastructure.QuizQuestionRepository;
import com.digishield.shared.tenantcontext.DemoTenants;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Seeds demo Learning data (courses, lessons, a quiz, enrollments, certificate,
 * badges, leaderboard and compliance policies) for the {@code dev} profile.
 * <p>
 * All rows are scoped to the fixed demo tenant {@link DemoTenants#DEMO_TENANT_ID}.
 */
@Component
@Profile("dev | seed")
@Order(20)
class LearningDevSeeder implements CommandLineRunner {

    /** Fixed demo learner ("Minh") used by the Learner portal seeds. */
    static final UUID DEMO_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID TENANT = DemoTenants.DEMO_TENANT_ID;

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CertificateRepository certificateRepository;
    private final BadgeRepository badgeRepository;
    private final GamificationProfileRepository gamificationProfileRepository;
    private final CompliancePolicyRepository compliancePolicyRepository;
    private final AssessmentRepository assessmentRepository;
    private final CoachingPageRepository coachingPageRepository;
    private final PointRuleRepository pointRuleRepository;

    LearningDevSeeder(CourseRepository courseRepository,
                      LessonRepository lessonRepository,
                      QuizQuestionRepository quizQuestionRepository,
                      EnrollmentRepository enrollmentRepository,
                      CertificateRepository certificateRepository,
                      BadgeRepository badgeRepository,
                      GamificationProfileRepository gamificationProfileRepository,
                      CompliancePolicyRepository compliancePolicyRepository,
                      AssessmentRepository assessmentRepository,
                      CoachingPageRepository coachingPageRepository,
                      PointRuleRepository pointRuleRepository) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.certificateRepository = certificateRepository;
        this.badgeRepository = badgeRepository;
        this.gamificationProfileRepository = gamificationProfileRepository;
        this.compliancePolicyRepository = compliancePolicyRepository;
        this.assessmentRepository = assessmentRepository;
        this.coachingPageRepository = coachingPageRepository;
        this.pointRuleRepository = pointRuleRepository;
    }

    private void seedPointRules() {
        if (!pointRuleRepository.findByTenantIdOrderByPointsDesc(TENANT).isEmpty()) {
            return;
        }
        pointRuleRepository.save(new PointRule(
                UUID.randomUUID(), TENANT, "report_confirmed", "Báo cáo email lừa đảo đúng", 50));
        pointRuleRepository.save(new PointRule(
                UUID.randomUUID(), TENANT, "quiz_passed", "Đạt bài kiểm tra (>=70%)", 24));
        pointRuleRepository.save(new PointRule(
                UUID.randomUUID(), TENANT, "lesson_completed", "Hoàn thành bài học", 10));
        pointRuleRepository.save(new PointRule(
                UUID.randomUUID(), TENANT, "simulation_clicked", "Bấm link mô phỏng", -5));
    }

    @Override
    public void run(String... args) {
        seedPointRules();
        if (!courseRepository.findByTenantId(TENANT).isEmpty()) {
            return; // idempotent
        }

        // 4 courses: one completed, one in-progress, two locked.
        Course basic = new Course(UUID.randomUUID(), TENANT, "An toàn cơ bản",
                CourseLevel.BASIC, "vi", 12, 3, 1);
        Course phishing = new Course(UUID.randomUUID(), TENANT, "Chống Phishing",
                CourseLevel.BEGINNER, "vi", 18, 5, 2);
        Course vishing = new Course(UUID.randomUUID(), TENANT, "Vishing nâng cao",
                CourseLevel.INTERMEDIATE, "vi", 15, 4, 3);
        Course deepfake = new Course(UUID.randomUUID(), TENANT, "Deepfake & AI lừa đảo",
                CourseLevel.ADVANCED, "vi", 22, 5, 4);
        courseRepository.saveAll(List.of(basic, phishing, vishing, deepfake));

        // Enrollments: basic completed, phishing in-progress (others stay locked).
        enrollmentRepository.save(new Enrollment(UUID.randomUUID(), TENANT, DEMO_USER_ID,
                basic.getId(), EnrollmentStatus.COMPLETED, 100, 100));
        enrollmentRepository.save(new Enrollment(UUID.randomUUID(), TENANT, DEMO_USER_ID,
                phishing.getId(), EnrollmentStatus.IN_PROGRESS, null, 60));

        // A lesson on the phishing course + a 5-question quiz.
        Lesson lesson = new Lesson(UUID.randomUUID(), TENANT, phishing.getId(),
                "Bài 3 · SMS Brandname giả",
                "Tin nhắn SMS Brandname giả là hình thức tấn công nguy hiểm vì kẻ tấn công có thể "
                        + "chèn vào đúng luồng tin nhắn thật của ngân hàng, khiến người dùng mất cảnh giác.",
                "Ví dụ thực tế",
                "Kẻ tấn công gửi SMS từ Brandname \"VietcomBank\": \"Tài khoản của bạn có dấu hiệu "
                        + "bất thường. Xác minh ngay: bit.ly/vbc-xacminh\"",
                "Dấu hiệu nhận biết: link rút gọn (bit.ly), yêu cầu bấm ngay, "
                        + "không có số điện thoại liên hệ chính thức.",
                "Khái niệm cơ bản,Cách nhận biết,Cách xử lý,Tóm tắt",
                12, 1);
        lessonRepository.save(lesson);

        seedQuiz(lesson.getId());

        // 1 certificate (for the completed phishing-awareness course).
        certificateRepository.save(new Certificate(
                UUID.randomUUID(), TENANT, DEMO_USER_ID, basic.getId(),
                "DS-2026-07A3-F91C", "Nhận diện & Phòng chống Phishing", "Nguyễn Minh", 92,
                Instant.parse("2026-06-25T00:00:00Z"),
                Instant.parse("2027-06-25T00:00:00Z"),
                "portal.digishield.vn"));

        // Badges for the demo learner.
        badgeRepository.save(new Badge(UUID.randomUUID(), TENANT, DEMO_USER_ID,
                "Người canh gác", "Hoàn thành 3 khóa học đầu tiên", "shield", true,
                Instant.now().minus(10, ChronoUnit.DAYS)));
        badgeRepository.save(new Badge(UUID.randomUUID(), TENANT, DEMO_USER_ID,
                "Thợ săn phishing", "Báo cáo đúng 5 email mô phỏng", "target", true,
                Instant.now().minus(3, ChronoUnit.DAYS)));
        badgeRepository.save(new Badge(UUID.randomUUID(), TENANT, DEMO_USER_ID,
                "Phản ứng nhanh", "Báo cáo trong 60 giây sau khi nhận email", "zap", false, null));
        badgeRepository.save(new Badge(UUID.randomUUID(), TENANT, DEMO_USER_ID,
                "Zero-click champion", "Không bấm link mô phỏng nào trong quý", "award", false, null));

        // Leaderboard rows (the demo learner is rank 2).
        gamificationProfileRepository.save(new GamificationProfile(UUID.randomUUID(), TENANT,
                UUID.randomUUID(), "Lan Anh", "Kế toán", 1580));
        gamificationProfileRepository.save(new GamificationProfile(UUID.randomUUID(), TENANT,
                DEMO_USER_ID, "Minh (bạn)", "Kế toán", 1240));
        gamificationProfileRepository.save(new GamificationProfile(UUID.randomUUID(), TENANT,
                UUID.randomUUID(), "Văn Bảo", "Kế toán", 1105));

        // 4 compliance policies.
        compliancePolicyRepository.save(new CompliancePolicy(UUID.randomUUID(), TENANT,
                "Đào tạo nhận thức an ninh cơ bản", "ISO27001",
                "Hạn: 31/12/2026 · Bắt buộc mọi nhân viên", true, 94));
        compliancePolicyRepository.save(new CompliancePolicy(UUID.randomUUID(), TENANT,
                "Chống phishing nâng cao", "ISO27001",
                "Hạn: 30/06/2026 · Phòng Kế toán, Kinh doanh", true, 71));
        compliancePolicyRepository.save(new CompliancePolicy(UUID.randomUUID(), TENANT,
                "Bảo vệ dữ liệu cá nhân (PDPA)", "PDPA",
                "Hạn: 30/09/2026 · Mọi nhân viên", true, 88));
        compliancePolicyRepository.save(new CompliancePolicy(UUID.randomUUID(), TENANT,
                "An toàn thiết bị di động", "NIST",
                "Hạn: 31/10/2026 · Khuyến nghị", false, 62));

        // A second certificate (completed "An toàn cơ bản" course) for the demo learner.
        certificateRepository.save(new Certificate(
                UUID.randomUUID(), TENANT, DEMO_USER_ID, basic.getId(),
                "DS-2026-01B7-2D4E", "An toàn cơ bản", "Nguyễn Minh", 88,
                Instant.parse("2026-05-10T00:00:00Z"),
                Instant.parse("2027-05-10T00:00:00Z"),
                "portal.digishield.vn"));

        // 3 assessments: a knowledge check, a culture survey and a placement test.
        assessmentRepository.save(new Assessment(UUID.randomUUID(), TENANT,
                AssessmentType.KNOWLEDGE, false,
                "{\"questions\":[{\"q\":\"Dấu hiệu email lừa đảo?\",\"options\":"
                        + "[\"Logo ngân hàng\",\"Tên miền sai chính tả\"]}]}",
                "2026-Q2", 128));
        assessmentRepository.save(new Assessment(UUID.randomUUID(), TENANT,
                AssessmentType.CULTURE, true,
                "{\"questions\":[{\"q\":\"Bạn có thoải mái báo cáo sự cố bảo mật?\","
                        + "\"scale\":5}]}",
                "2026-Q2", 96));
        assessmentRepository.save(new Assessment(UUID.randomUUID(), TENANT,
                AssessmentType.PLACEMENT, false,
                "{\"questions\":[{\"q\":\"Phishing là gì?\"},{\"q\":\"Vishing là gì?\"},"
                        + "{\"q\":\"Deepfake là gì?\"}]}",
                "2026-Q2", 42));

        // 2 just-in-time coaching pages shown after a risky action.
        coachingPageRepository.save(new CoachingPage(UUID.randomUUID(), TENANT,
                UUID.randomUUID(), "coaching/phishing-click-vi",
                "{\"signals\":[\"Link rút gọn (bit.ly)\",\"Yêu cầu bấm ngay\","
                        + "\"Tên miền sai chính tả\"]}"));
        coachingPageRepository.save(new CoachingPage(UUID.randomUUID(), TENANT,
                UUID.randomUUID(), "coaching/vishing-callback-vi",
                "{\"signals\":[\"Gọi từ số lạ\",\"Tạo cảm giác khẩn cấp\","
                        + "\"Yêu cầu cung cấp OTP\"]}"));
    }

    private void seedQuiz(UUID lessonId) {
        quizQuestionRepository.save(new QuizQuestion(UUID.randomUUID(), TENANT, lessonId,
                "Đâu là dấu hiệu chắc chắn nhất của email lừa đảo?",
                "Email gắn logo ngân hàng chính hãng",
                "Tên miền người gửi sai chính tả (bank-vn.support)",
                "Email gửi vào ban đêm",
                "Email có chữ ký điện tử",
                1, "Tên miền sai chính tả là dấu hiệu chắc chắn nhất — logo ngân hàng có thể sao chép.", 1));
        quizQuestionRepository.save(new QuizQuestion(UUID.randomUUID(), TENANT, lessonId,
                "Nhận SMS yêu cầu nhấp link xác nhận tài khoản ngân hàng, bạn nên?",
                "Nhấp link và đăng nhập ngay",
                "Gọi hotline chính thức của ngân hàng để xác nhận",
                "Chuyển tiếp cho đồng nghiệp để hỏi",
                "Bỏ qua toàn bộ SMS ngân hàng",
                1, "Luôn xác minh qua hotline in ở mặt sau thẻ, không nhấp link trong SMS.", 2));
        quizQuestionRepository.save(new QuizQuestion(UUID.randomUUID(), TENANT, lessonId,
                "SMS Brandname giả nguy hiểm vì điều gì?",
                "Chèn vào luồng SMS thật của ngân hàng, khiến mất cảnh giác",
                "Gửi từ số quốc tế lạ",
                "Có nhiều hình ảnh đính kèm",
                "Luôn có lỗi chính tả trong nội dung",
                0, "Kẻ tấn công chèn vào đúng thread SMS Brandname của ngân hàng thật.", 3));
        quizQuestionRepository.save(new QuizQuestion(UUID.randomUUID(), TENANT, lessonId,
                "Email nào dưới đây là THẬT?",
                "support@banking-vn.com — đổi mật khẩu ngay",
                "noreply@techcombank.com.vn — xác nhận giao dịch ATM",
                "security@vietcombank-alert.net — cảnh báo bảo mật",
                "info@mbbank-online.support — thông báo trúng thưởng",
                1, "Miền .com.vn với tên ngân hàng chính thức là hợp lệ.", 4));
        quizQuestionRepository.save(new QuizQuestion(UUID.randomUUID(), TENANT, lessonId,
                "Sau khi báo cáo email lừa đảo, bạn nên làm gì?",
                "Chỉ xóa email đó ngay lập tức",
                "Chỉ báo đồng nghiệp biết để cảnh giác",
                "Đợi SOC phản hồi, không làm gì thêm",
                "Xóa email, báo đồng nghiệp và đợi SOC xác nhận",
                3, "Xóa để an toàn, chia sẻ để cảnh giác, và đợi SOC xác nhận.", 5));
    }
}
