package com.digishield.notification.application;

import com.digishield.notification.api.NotificationService;
import com.digishield.notification.domain.Notification;
import com.digishield.notification.domain.NotificationChannel;
import com.digishield.notification.domain.NotificationStatus;
import com.digishield.notification.domain.NotificationType;
import com.digishield.notification.infrastructure.NotificationRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link NotificationService}. Minimal body for the skeleton.
 */
@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository repository;

    public NotificationServiceImpl(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Notification send(UUID userId, NotificationType type, NotificationChannel channel, String title, String body) {
        UUID tenantId = TenantContext.requireUuid();
        Notification notification = new Notification(
                UUID.randomUUID(), tenantId, userId, type, channel,
                NotificationStatus.SENT, title, body, Instant.now());
        // TODO: integrate a real gateway (email/sms/push) before marking SENT.
        return repository.save(notification);
    }

    @Override
    public Notification scheduleReminder(UUID userId, String title, String body) {
        UUID tenantId = TenantContext.requireUuid();
        Notification notification = new Notification(
                UUID.randomUUID(), tenantId, userId, NotificationType.REMINDER, NotificationChannel.IN_APP,
                NotificationStatus.SCHEDULED, title, body, Instant.now());
        return repository.save(notification);
    }

    @Override
    public Notification broadcastAlert(UUID userId, String title, String body) {
        UUID tenantId = TenantContext.requireUuid();
        Notification notification = new Notification(
                UUID.randomUUID(), tenantId, userId, NotificationType.ALERT, NotificationChannel.IN_APP,
                NotificationStatus.SENT, title, body, Instant.now());
        // TODO: extend to send to multiple users based on criteria (segment).
        return repository.save(notification);
    }

    /**
     * Creates a reminder without going through TenantContext (used by event listeners that already have the tenant).
     */
    public Notification createReminderForTenant(UUID tenantId, UUID userId, String title, String body) {
        Notification notification = new Notification(
                UUID.randomUUID(), tenantId, userId, NotificationType.REMINDER, NotificationChannel.IN_APP,
                NotificationStatus.SCHEDULED, title, body, Instant.now());
        return repository.save(notification);
    }
}
