package com.digishield.notification.web;

import com.digishield.notification.api.NotificationService;
import com.digishield.notification.api.NotificationView;
import com.digishield.notification.domain.Notification;
import com.digishield.notification.infrastructure.NotificationRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import java.util.List;
import java.util.UUID;
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
     * Broadcasts an alert to a user (sample).
     */
    @PostMapping("/api/v1/alerts/broadcast")
    public Notification broadcast(@RequestBody BroadcastAlertRequest request) {
        return notificationService.broadcastAlert(request.userId(), request.title(), request.body());
    }

    /** DTO for broadcast alert request. */
    public record BroadcastAlertRequest(UUID userId, String title, String body) {
    }
}
