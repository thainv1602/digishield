package com.digishield.ai.application;

import com.digishield.ai.domain.AidaRun;
import com.digishield.ai.domain.AiTemplate;
import com.digishield.ai.domain.Difficulty;
import com.digishield.ai.domain.TemplateChannel;
import com.digishield.ai.domain.TemplateStatus;
import com.digishield.ai.infrastructure.AidaRunRepository;
import com.digishield.ai.infrastructure.AiTemplateRepository;
import com.digishield.shared.tenantcontext.DemoTenants;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Seeds demo AI-generated template drafts for the {@code dev} profile so the
 * simulation builder / template library renders against a real datasource. All
 * rows are scoped to the fixed demo tenant.
 */
@Component
@Profile("dev")
@Order(20)
public class AiDevSeeder implements CommandLineRunner {

    private static final UUID DEMO_TENANT = DemoTenants.DEMO_TENANT_ID;

    private final AiTemplateRepository templateRepository;
    private final AidaRunRepository aidaRunRepository;

    public AiDevSeeder(AiTemplateRepository templateRepository, AidaRunRepository aidaRunRepository) {
        this.templateRepository = templateRepository;
        this.aidaRunRepository = aidaRunRepository;
    }

    @Override
    public void run(String... args) {
        seedTemplates();
        seedAidaRuns();
    }

    private void seedTemplates() {
        if (!templateRepository.findByTenantId(DEMO_TENANT).isEmpty()) {
            return;
        }

        // Realistic Vietnamese lures impersonating public bodies (tax, social
        // insurance, gov e-service), plus banking/utility — training content only.
        templateRepository.save(new AiTemplate(
                UUID.randomUUID(), DEMO_TENANT, TemplateChannel.EMAIL,
                "[Tổng cục Thuế] Thông báo hoàn thuế thu nhập cá nhân 2025",
                "tmpl/email/thue-hoan", """
                        Kính gửi Người nộp thuế,

                        Cơ quan Thuế xác định bạn đủ điều kiện được hoàn thuế TNCN năm 2025 với số tiền
                        2.480.000đ. Vui lòng đăng nhập Cổng dịch vụ và xác nhận thông tin tài khoản ngân
                        hàng trước ngày 30/07/2026 để nhận hoàn thuế, nếu không hồ sơ sẽ bị hủy.

                        Xác nhận hoàn thuế: https://hoanthue-tct.example.vn

                        Trân trọng,
                        Tổng cục Thuế""",
                "Cơ quan thuế", Difficulty.HARD, TemplateStatus.APPROVED));

        templateRepository.save(new AiTemplate(
                UUID.randomUUID(), DEMO_TENANT, TemplateChannel.SMS,
                "[BHXH] Cập nhật thông tin sổ bảo hiểm",
                "tmpl/sms/bhxh", "BHXH: So bao hiem cua ban chua duoc dong bo VssID. "
                        + "Cap nhat trong 24h de tranh gian doan quyen loi: https://vssid-capnhat.example.vn",
                "Bảo hiểm xã hội", Difficulty.MEDIUM, TemplateStatus.APPROVED));

        templateRepository.save(new AiTemplate(
                UUID.randomUUID(), DEMO_TENANT, TemplateChannel.EMAIL,
                "[Dịch vụ công] Hồ sơ định danh mức 2 cần bổ sung",
                "tmpl/email/dvc-dinhdanh", """
                        Kính gửi Công dân,

                        Hồ sơ định danh điện tử mức 2 của bạn thiếu thông tin và sẽ bị khóa sau 48 giờ.
                        Vui lòng truy cập Cổng Dịch vụ công và đăng nhập để bổ sung, xác thực ngay.

                        Bổ sung hồ sơ: https://dichvucong-xacthuc.example.vn

                        Trân trọng,
                        Cổng Dịch vụ công Quốc gia""",
                "Cơ quan nhà nước", Difficulty.HARD, TemplateStatus.DRAFT));

        templateRepository.save(new AiTemplate(
                UUID.randomUUID(), DEMO_TENANT, TemplateChannel.EMAIL,
                "[Ngân hàng] Cảnh báo đăng nhập bất thường",
                "tmpl/email/ngan-hang", """
                        Kính gửi Quý khách,

                        Chúng tôi ghi nhận một lần đăng nhập bất thường vào tài khoản của Quý khách. Nếu
                        không phải bạn, vui lòng xác minh danh tính ngay để tránh tài khoản bị tạm khóa.

                        Xác minh ngay: https://xacminh-nganhang.example.vn""",
                "Ngân hàng", Difficulty.MEDIUM, TemplateStatus.DRAFT));

        templateRepository.save(new AiTemplate(
                UUID.randomUUID(), DEMO_TENANT, TemplateChannel.SMS,
                "[EVN] Cảnh báo nợ tiền điện",
                "tmpl/sms/evn", "EVN: Hoa don tien dien thang nay chua thanh toan, dien se bi cat trong "
                        + "hom nay. Thanh toan ngay: https://evn-thanhtoan.example.vn",
                "Điện lực", Difficulty.EASY, TemplateStatus.APPROVED));

        templateRepository.save(new AiTemplate(
                UUID.randomUUID(), DEMO_TENANT, TemplateChannel.ZALO,
                "[Bảo hiểm] Nhận quyền lợi hợp đồng đến hạn",
                "tmpl/zalo/baohiem", "Hop dong bao hiem cua ban co quyen loi den han. Quet ma / nhan lien "
                        + "ket de xac nhan nhan tien: https://baohiem-quyenloi.example.vn",
                "Bảo hiểm", Difficulty.HARD, TemplateStatus.DRAFT));
    }

    private void seedAidaRuns() {
        if (!aidaRunRepository.findByTenantIdOrderByCreatedAtDesc(DEMO_TENANT).isEmpty()) {
            return;
        }
        Instant now = Instant.now();

        aidaRunRepository.save(new AidaRun(
                UUID.randomUUID(), DEMO_TENANT, "org", null, "success",
                "87 người được tự động đăng ký khóa học mới.", now.minus(Duration.ofDays(4))));

        aidaRunRepository.save(new AidaRun(
                UUID.randomUUID(), DEMO_TENANT, "Phòng Kế toán", null, "success",
                "34 người được cập nhật lộ trình học.", now.minus(Duration.ofDays(19))));
    }
}
