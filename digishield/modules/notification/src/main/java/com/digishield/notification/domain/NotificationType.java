package com.digishield.notification.domain;

/**
 * Notification type by business purpose.
 */
public enum NotificationType {
    /** Reminder (e.g. complete a course). */
    REMINDER,
    /** Alert (e.g. risk detected). */
    ALERT,
    /** System notification. */
    SYSTEM
}
