package com.digishield.notification.domain;

/**
 * Lifecycle status of a notification.
 */
public enum NotificationStatus {
    /** Scheduled, not yet sent. */
    SCHEDULED,
    /** Sent. */
    SENT,
    /** Read by the user. */
    READ
}
