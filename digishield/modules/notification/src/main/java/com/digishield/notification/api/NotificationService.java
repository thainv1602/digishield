package com.digishield.notification.api;

import com.digishield.notification.domain.Notification;
import com.digishield.notification.domain.NotificationChannel;
import com.digishield.notification.domain.NotificationStatus;
import com.digishield.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
     * Broadcasts an in-app ALERT to every user in the current tenant. Returns the
     * notifications created (one per recipient); the size is the reach.
     */
    java.util.List<Notification> broadcastAlert(String title, String body);

    /**
     * Persists a notification with explicit attributes (used by {@code POST /notifications}).
     * Any {@code null} enum or timestamp falls back to a sensible default.
     *
     * @param userId      target user (may be {@code null} for an org-wide row)
     * @param type        notification type (default {@link NotificationType#SYSTEM})
     * @param channel     delivery channel (default {@link NotificationChannel#IN_APP})
     * @param status      lifecycle status (default {@link NotificationStatus#SENT})
     * @param title       short title
     * @param body        body text
     * @param scheduledAt scheduled / created timestamp (default now)
     */
    Notification create(UUID userId, NotificationType type, NotificationChannel channel,
                        NotificationStatus status, String title, String body, Instant scheduledAt);

    /**
     * Schedules mandatory-training REMINDER notifications for the users selected by
     * {@code targetFilter}, on the given {@code channel}, with a due timestamp derived
     * from {@code dueRule} (e.g. {@code "before_due:3d"}). Used by
     * {@code POST /notifications/reminders}.
     *
     * @param targetFilter opaque selector; supports an optional {@code user_ids} list
     * @param dueRule      rule describing when the reminder is due
     * @param channel      delivery channel (default {@link NotificationChannel#IN_APP})
     * @return the scheduled reminder notifications
     */
    List<Notification> scheduleReminders(Map<String, Object> targetFilter, String dueRule, NotificationChannel channel);
}
