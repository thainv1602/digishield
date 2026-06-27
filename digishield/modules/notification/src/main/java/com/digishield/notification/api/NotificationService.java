package com.digishield.notification.api;

import com.digishield.notification.domain.Notification;
import com.digishield.notification.domain.NotificationChannel;
import com.digishield.notification.domain.NotificationType;
import java.util.UUID;

/**
 * Public API of the Notification module for other modules and the web layer.
 */
public interface NotificationService {

    /**
     * Immediately sends a notification to a user.
     */
    Notification send(UUID userId, NotificationType type, NotificationChannel channel, String title, String body);

    /**
     * Creates a reminder notification in the SCHEDULED state.
     */
    Notification scheduleReminder(UUID userId, String title, String body);

    /**
     * Broadcasts an alert (ALERT) to a user (minimal broadcast sample).
     */
    Notification broadcastAlert(UUID userId, String title, String body);
}
