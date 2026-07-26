package com.digishield.notification.application;

import com.digishield.contracts.events.SimulationDeliveryRequestedEvent;
import com.digishield.notification.api.NotificationService;
import com.digishield.notification.domain.NotificationChannel;
import com.digishield.notification.domain.NotificationType;
import com.digishield.shared.tenantcontext.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Delivers a launched simulation campaign's tracking link to each recipient over
 * the campaign channel. Listens for {@link SimulationDeliveryRequestedEvent} and
 * routes through {@link NotificationService}, which resolves the recipient's
 * email/phone and hands off to the configured gateway (real AWS SES/SNS when
 * enabled, otherwise the logging gateway — the send is still recorded).
 *
 * <p>Runs asynchronously in its own transaction ({@link ApplicationModuleListener}),
 * so it sets the tenant on the current thread before touching tenant-scoped data.
 */
@Component
class SimulationDeliveryListener {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationDeliveryListener.class);

    private final NotificationService notificationService;
    /** Absolute public base URL prepended to the tracking path in the message. */
    private final String publicBaseUrl;

    SimulationDeliveryListener(NotificationService notificationService,
                               @Value("${digishield.app.public-base-url:}") String publicBaseUrl) {
        this.notificationService = notificationService;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    @ApplicationModuleListener
    void on(SimulationDeliveryRequestedEvent event) {
        TenantContext.set(event.tenantId().toString());
        try {
            NotificationChannel channel = toChannel(event.channel());
            String link = publicBaseUrl + event.trackPath();
            String title = "DigiShield: thông báo cần xem";
            String body = "Bạn có một thông báo quan trọng. Vui lòng xem tại: " + link;
            notificationService.send(event.userId(), NotificationType.SYSTEM, channel, title, body);
        } catch (Exception e) {
            // Never let a delivery failure break the campaign launch.
            LOG.warn("Simulation delivery for user {} failed: {}", event.userId(), e.toString());
        } finally {
            TenantContext.clear();
        }
    }

    /** Maps a simulation channel to a deliverable notification channel. */
    private static NotificationChannel toChannel(String simChannel) {
        if (!StringUtils.hasText(simChannel)) {
            return NotificationChannel.EMAIL;
        }
        return switch (simChannel.toUpperCase()) {
            case "SMS", "VOICE" -> NotificationChannel.SMS;
            default -> NotificationChannel.EMAIL; // EMAIL, QR, ZALO, TEAMS, SLACK, USB
        };
    }
}
