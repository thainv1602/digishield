package com.digishield;

import com.digishield.notification.api.NotificationService;
import com.digishield.tenancy.api.DeliveryUsageProvider;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wires tenancy's {@link DeliveryUsageProvider} SPI to what the notification
 * module recorded, so usage is counted rather than seeded.
 * <p>
 * Only {@code email_sent} and {@code sms_sent} are reported. The dev seeder also
 * carried {@code ai_call} and {@code storage}; nothing in the system records
 * either, so nothing is returned for them — an absent metric says "not
 * measured", a zero would claim it was measured and came to nothing.
 */
@Component
class NotificationDeliveryUsage implements DeliveryUsageProvider {

    private final NotificationService notificationService;

    NotificationDeliveryUsage(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Map<String, Long> sentCounts(UUID tenantId, Instant from, Instant to) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("email_sent", notificationService.countSent(tenantId, "EMAIL", from, to));
        counts.put("sms_sent", notificationService.countSent(tenantId, "SMS", from, to));
        return counts;
    }
}
