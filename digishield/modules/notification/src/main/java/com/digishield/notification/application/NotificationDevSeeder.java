package com.digishield.notification.application;

import com.digishield.notification.domain.Notification;
import com.digishield.notification.domain.NotificationChannel;
import com.digishield.notification.domain.NotificationStatus;
import com.digishield.notification.domain.NotificationType;
import com.digishield.notification.infrastructure.NotificationRepository;
import com.digishield.shared.tenantcontext.DemoTenants;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Seeds ~5 demo notifications (reminder / alert / system) for the bell dropdown
 * and Alert Center in the {@code dev} profile. All rows are scoped to the fixed
 * demo tenant {@link DemoTenants#DEMO_TENANT_ID}.
 */
@Component
@Profile("dev")
@Order(20)
class NotificationDevSeeder implements CommandLineRunner {

    private static final UUID TENANT = DemoTenants.DEMO_TENANT_ID;
    private static final UUID DEMO_USER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final NotificationRepository repository;

    NotificationDevSeeder(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (!repository.findByTenantId(TENANT).isEmpty()) {
            return; // idempotent
        }
        Instant now = Instant.now();

        repository.save(new Notification(UUID.randomUUID(), TENANT, DEMO_USER,
                NotificationType.ALERT, NotificationChannel.IN_APP, NotificationStatus.SENT,
                "Chiến dịch SMS giả ngân hàng đang hoạt động",
                "Cảnh báo CRITICAL: phát hiện chiến dịch SMS Brandname giả mạo nhắm vào nhân viên.",
                now.minus(2, ChronoUnit.HOURS)));

        repository.save(new Notification(UUID.randomUUID(), TENANT, DEMO_USER,
                NotificationType.REMINDER, NotificationChannel.IN_APP, NotificationStatus.SENT,
                "Hoàn thành \"Bài kiểm tra Phishing\"",
                "Hạn 30/06 · Còn 3 ngày để hoàn thành bài đào tạo bắt buộc.",
                now.minus(1, ChronoUnit.DAYS)));

        repository.save(new Notification(UUID.randomUUID(), TENANT, DEMO_USER,
                NotificationType.REMINDER, NotificationChannel.EMAIL, NotificationStatus.SCHEDULED,
                "Nhắc lịch đào tạo bắt buộc Q3",
                "Khóa \"Chống phishing nâng cao\" sẽ mở vào 01/07.",
                now.plus(2, ChronoUnit.DAYS)));

        repository.save(new Notification(UUID.randomUUID(), TENANT, DEMO_USER,
                NotificationType.SYSTEM, NotificationChannel.IN_APP, NotificationStatus.READ,
                "Chứng chỉ mới đã được cấp",
                "Bạn vừa nhận chứng chỉ \"Nhận diện & Phòng chống Phishing\".",
                now.minus(2, ChronoUnit.DAYS)));

        repository.save(new Notification(UUID.randomUUID(), TENANT, DEMO_USER,
                NotificationType.SYSTEM, NotificationChannel.IN_APP, NotificationStatus.SENT,
                "Cập nhật chính sách bảo mật",
                "Chính sách PDPA đã được cập nhật. Vui lòng đọc và xác nhận.",
                now.minus(5, ChronoUnit.DAYS)));

        // Created ad-hoc via POST /notifications (alert pushed to a user).
        repository.save(new Notification(UUID.randomUUID(), TENANT, DEMO_USER,
                NotificationType.ALERT, NotificationChannel.EMAIL, NotificationStatus.SENT,
                "Đăng nhập bất thường được phát hiện",
                "Tài khoản của bạn vừa đăng nhập từ thiết bị lạ. Hãy kiểm tra ngay nếu không phải bạn.",
                now.minus(30, ChronoUnit.MINUTES)));

        // Scheduled batch via POST /notifications/reminders (due_rule "before_due:3d", email channel).
        repository.save(new Notification(UUID.randomUUID(), TENANT, DEMO_USER,
                NotificationType.REMINDER, NotificationChannel.EMAIL, NotificationStatus.SCHEDULED,
                "Nhắc hoàn thành đào tạo bắt buộc",
                "Bạn có khoá đào tạo bắt buộc cần hoàn thành (quy tắc: before_due:3d).",
                now.plus(3, ChronoUnit.DAYS)));
    }
}
