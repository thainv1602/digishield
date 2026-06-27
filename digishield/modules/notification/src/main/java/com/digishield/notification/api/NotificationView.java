package com.digishield.notification.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view describing a notification (bell dropdown + Alert Center).
 * <p>
 * Field names follow the OpenAPI {@code Notification} schema; enum values are
 * lower-case strings matching the FE (type: reminder|alert|system,
 * channel: in_app|email|sms, status: scheduled|sent|read).
 *
 * @param id          notification identifier
 * @param userId      target user
 * @param type        notification type
 * @param channel     delivery channel
 * @param status      lifecycle status
 * @param title       short title
 * @param body        body text
 * @param scheduledAt scheduled / created timestamp
 */
public record NotificationView(UUID id, UUID userId, String type, String channel,
                               String status, String title, String body, Instant scheduledAt) {
}
