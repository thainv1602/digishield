package com.digishield.notification.web;

import com.digishield.notification.api.NotificationService;
import com.digishield.notification.api.NotificationView;
import com.digishield.notification.domain.Notification;
import com.digishield.notification.domain.NotificationChannel;
import com.digishield.notification.domain.NotificationStatus;
import com.digishield.notification.domain.NotificationType;
import com.digishield.notification.infrastructure.NotificationRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the Notification module (bell dropdown + Alert Center).
 */
@RestController
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRepository repository;

    public NotificationController(NotificationService notificationService, NotificationRepository repository) {
        this.notificationService = notificationService;
        this.repository = repository;
    }

    /**
     * Lists notifications of the current tenant.
     */
    @GetMapping("/api/v1/notifications")
    public List<NotificationView> list() {
        UUID tenantId = TenantContext.requireUuid();
        return repository.findByTenantId(tenantId).stream()
                .map(NotificationController::toView)
                .toList();
    }

    /**
     * Creates (persists) a notification for the current tenant. Matches
     * {@code POST /notifications} (Notification schema body, snake_case wire names).
     */
    @PostMapping("/api/v1/notifications")
    public ResponseEntity<NotificationView> create(@RequestBody CreateNotificationRequest request) {
        Map<String, Object> payload = request.payload();
        Notification created = notificationService.create(
                request.userId(),
                parseType(request.type()),
                parseChannel(request.channel()),
                parseStatus(request.status()),
                payloadString(payload, "title"),
                payloadString(payload, "body"),
                request.scheduledAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(created));
    }

    /**
     * Schedules mandatory-training REMINDER notifications. Matches
     * {@code POST /notifications/reminders} ({target_filter, due_rule, channel}) and
     * returns {@code 202 Accepted} with the number of reminders scheduled.
     */
    @PostMapping("/api/v1/notifications/reminders")
    public ResponseEntity<ScheduleRemindersResponse> scheduleReminders(
            @RequestBody ScheduleRemindersRequest request) {
        List<Notification> scheduled = notificationService.scheduleReminders(
                request.targetFilter(), request.dueRule(), parseChannel(request.channel()));
        Instant dueAt = scheduled.isEmpty() ? null : scheduled.get(0).getCreatedAt();
        return ResponseEntity.accepted()
                .body(new ScheduleRemindersResponse(scheduled.size(), request.dueRule(), dueAt));
    }

    private static String payloadString(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get(key);
        if (value == null && payload.get("message") != null) {
            return key.equals("body") ? String.valueOf(payload.get("message")) : null;
        }
        return value != null ? String.valueOf(value) : null;
    }

    private static NotificationType parseType(String value) {
        return value == null || value.isBlank() ? null : NotificationType.valueOf(value.trim().toUpperCase());
    }

    private static NotificationChannel parseChannel(String value) {
        return value == null || value.isBlank() ? null : NotificationChannel.valueOf(value.trim().toUpperCase());
    }

    private static NotificationStatus parseStatus(String value) {
        return value == null || value.isBlank() ? null : NotificationStatus.valueOf(value.trim().toUpperCase());
    }

    private static NotificationView toView(Notification n) {
        return new NotificationView(
                n.getId(),
                n.getUserId(),
                n.getType() != null ? n.getType().name().toLowerCase() : null,
                n.getChannel() != null ? n.getChannel().name().toLowerCase() : null,
                n.getStatus() != null ? n.getStatus().name().toLowerCase() : null,
                n.getTitle(),
                n.getBody(),
                n.getCreatedAt());
    }

    /**
     * Broadcasts an alert to every user in the current tenant. Returns the reach
     * (number of recipients).
     */
    @PostMapping("/api/v1/alerts/broadcast")
    public BroadcastResult broadcast(@RequestBody BroadcastAlertRequest request) {
        String severity = (request.severity() == null || request.severity().isBlank())
                ? "info" : request.severity().trim();
        String title = "[" + severity.toUpperCase(java.util.Locale.ROOT) + "] " + request.message();
        int reach = notificationService.broadcastAlert(title, request.message()).size();
        return new BroadcastResult(reach);
    }

    /** Broadcast request, mirroring the OpenAPI {@code PostAlertsBroadcastBody}. */
    public record BroadcastAlertRequest(String message, String severity) {
    }

    /** Broadcast result: how many recipients the alert reached. */
    public record BroadcastResult(int reach) {
    }

    /**
     * Create-notification request mirroring the OpenAPI {@code Notification} schema
     * (snake_case wire names; enum values are lower-case strings).
     *
     * @param userId      target user
     * @param type        notification type (reminder|alert|system)
     * @param channel     delivery channel (in_app|email|sms)
     * @param payload     free-form payload; {@code title}/{@code body}/{@code message} are read out
     * @param status      lifecycle status (scheduled|sent|read)
     * @param scheduledAt scheduled / created timestamp
     */
    public record CreateNotificationRequest(
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("type") String type,
            @JsonProperty("channel") String channel,
            @JsonProperty("payload") Map<String, Object> payload,
            @JsonProperty("status") String status,
            @JsonProperty("scheduled_at") Instant scheduledAt) {
    }

    /**
     * Reminder-scheduling request ({@code target_filter}, {@code due_rule}, {@code channel}).
     *
     * @param targetFilter opaque audience selector (supports an optional {@code user_ids} list)
     * @param dueRule      rule describing when the reminder is due (e.g. {@code "before_due:3d"})
     * @param channel      delivery channel (in_app|email|sms)
     */
    public record ScheduleRemindersRequest(
            @JsonProperty("target_filter") Map<String, Object> targetFilter,
            @JsonProperty("due_rule") String dueRule,
            @JsonProperty("channel") String channel) {
    }

    /**
     * Response acknowledging scheduled reminders.
     *
     * @param scheduled number of reminders created
     * @param dueRule   the due rule applied
     * @param dueAt     resolved due timestamp shared by the scheduled reminders
     */
    public record ScheduleRemindersResponse(
            @JsonProperty("scheduled") int scheduled,
            @JsonProperty("due_rule") String dueRule,
            @JsonProperty("due_at") Instant dueAt) {
    }
}
