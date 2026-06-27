package com.digishield.reporting.application;

import com.digishield.reporting.domain.AiLabel;
import com.digishield.reporting.domain.BlacklistEntry;
import com.digishield.reporting.domain.BlacklistType;
import com.digishield.reporting.domain.PhishingReport;
import com.digishield.reporting.domain.ReportStatus;
import com.digishield.reporting.infrastructure.BlacklistEntryRepository;
import com.digishield.reporting.infrastructure.PhishingReportRepository;
import com.digishield.shared.tenantcontext.DemoTenants;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Seeds demo phishing reports and blacklist entries for the {@code dev} profile
 * so the SOC inbox and watchlist screens render against a real datasource.
 * All rows are scoped to the fixed demo tenant.
 */
@Component
@Profile("dev")
@Order(20)
public class ReportingDevSeeder implements CommandLineRunner {

    private static final UUID DEMO_TENANT = DemoTenants.DEMO_TENANT_ID;

    private final PhishingReportRepository reportRepository;
    private final BlacklistEntryRepository blacklistRepository;

    public ReportingDevSeeder(PhishingReportRepository reportRepository,
                              BlacklistEntryRepository blacklistRepository) {
        this.reportRepository = reportRepository;
        this.blacklistRepository = blacklistRepository;
    }

    @Override
    public void run(String... args) {
        if (!reportRepository.findByTenantId(DEMO_TENANT).isEmpty()) {
            return;
        }

        Instant now = Instant.now();

        seedReport("Nguyễn Minh An", "no-reply@bank-vn.support",
                "\"Khóa tài khoản — xác minh ngay\"",
                AiLabel.THREAT, 0.96, ReportStatus.SUBMITTED,
                "Tên miền giả mạo tổ chức tài chính + yêu cầu thông tin xác thực + khớp pattern known phishing campaign",
                true, now.minus(2, ChronoUnit.MINUTES));

        seedReport("Trần Thị Bình", "promo@win-prizes.biz",
                "\"Chúc mừng! Bạn trúng iPhone 15\"",
                AiLabel.SPAM, 0.71, ReportStatus.SUBMITTED,
                "Ngôn ngữ trúng thưởng + liên kết rút gọn đáng ngờ",
                false, now.minus(8, ChronoUnit.MINUTES));

        seedReport("Lê Văn Cường", "lich-hop@abc.gov.vn",
                "\"Lịch họp tuần 27 – phòng họp A3\"",
                AiLabel.CLEAN, 0.99, ReportStatus.DISMISSED,
                "Người gửi nội bộ hợp lệ, không có liên kết độc hại",
                false, now.minus(15, ChronoUnit.MINUTES));

        seedReport("Phạm Đức Dũng", "alert@acb-online.help",
                "\"Xác nhận giao dịch ACB 48.5 triệu\"",
                AiLabel.THREAT, 0.88, ReportStatus.SUBMITTED,
                "Giả mạo ngân hàng ACB + tạo cảm giác khẩn cấp về giao dịch",
                true, now.minus(23, ChronoUnit.MINUTES));

        seedReport("Nguyễn Lan Anh", "trial@free-vpn-vn.net",
                "\"Mời dùng thử VPN miễn phí\"",
                AiLabel.SPAM, 0.62, ReportStatus.SUBMITTED,
                "Quảng cáo dịch vụ không rõ nguồn gốc",
                false, now.minus(31, ChronoUnit.MINUTES));

        seedReport("Hoàng Thị Mai", "it-support@abc.gov.vn",
                "\"Cập nhật chính sách mật khẩu nội bộ\"",
                AiLabel.CLEAN, 0.93, ReportStatus.DISMISSED,
                "Thông báo nội bộ từ phòng IT, hợp lệ",
                false, now.minus(52, ChronoUnit.MINUTES));

        // A few blacklist / watchlist entries.
        blacklistRepository.save(new BlacklistEntry(
                UUID.randomUUID(), DEMO_TENANT, BlacklistType.URL, "bank-vn.support", "NCSC"));
        blacklistRepository.save(new BlacklistEntry(
                UUID.randomUUID(), DEMO_TENANT, BlacklistType.URL, "acb-online.help", "NCSC"));
        blacklistRepository.save(new BlacklistEntry(
                UUID.randomUUID(), DEMO_TENANT, BlacklistType.PHONE, "+84900111222", "A05"));
        blacklistRepository.save(new BlacklistEntry(
                UUID.randomUUID(), DEMO_TENANT, BlacklistType.DOMAIN, "win-prizes.biz", "Internal"));
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void seedReport(String reporter, String sender, String subject, AiLabel label,
                            double confidence, ReportStatus status, String reason,
                            boolean blacklistMatch, Instant reportedAt) {
        reportRepository.save(new PhishingReport(
                UUID.randomUUID(), DEMO_TENANT, UUID.randomUUID(),
                "[demo sanitized payload]", label, confidence, status,
                reporter, subject, sender, reason, blacklistMatch, reportedAt));
    }
}
