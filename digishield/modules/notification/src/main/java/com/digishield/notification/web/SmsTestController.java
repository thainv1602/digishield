package com.digishield.notification.web;

import com.digishield.notification.api.NotificationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings endpoint to send a test SMS through the configured
 * {@link NotificationGateway}. When the real SNS gateway is enabled the message
 * is actually delivered; otherwise the logging gateway stands in and the call
 * succeeds as a simulation (no message leaves the system).
 */
@RestController
public class SmsTestController {

    private static final Logger LOG = LoggerFactory.getLogger(SmsTestController.class);

    private final NotificationGateway gateway;

    public SmsTestController(NotificationGateway gateway) {
        this.gateway = gateway;
    }

    @PreAuthorize("hasRole('ORG_ADMIN')")
    @PostMapping("/api/v1/settings/sms/test")
    public ResponseEntity<SmsTestResult> test(@RequestBody SmsTestRequest request) {
        String phone = request.phone() == null ? "" : request.phone().trim();
        if (!StringUtils.hasText(phone) || !phone.matches("\\+?[0-9 ]{6,20}")) {
            return ResponseEntity.badRequest().body(
                    new SmsTestResult(false, "Số điện thoại không hợp lệ (ví dụ +8490...)."));
        }
        String body = StringUtils.hasText(request.message())
                ? request.message().trim()
                : "DigiShield: tin nhắn kiểm tra cấu hình SMS.";
        try {
            gateway.deliver("SMS", phone, "DigiShield", body);
            return ResponseEntity.ok(new SmsTestResult(true, "Đã gửi qua cổng SMS đang cấu hình."));
        } catch (Exception e) {
            LOG.warn("Test SMS to {} failed: {}", phone, e.toString());
            return ResponseEntity.ok(new SmsTestResult(false, "Gửi thất bại: " + e.getMessage()));
        }
    }

    /** Test SMS request. */
    public record SmsTestRequest(String phone, String message) {
    }

    /** Test SMS outcome. */
    public record SmsTestResult(boolean delivered, String detail) {
    }
}
