package com.digishield.notification.api;

import java.util.UUID;

/**
 * SPI for pushing a notification to a tenant's connected clients in real time
 * (e.g. over WebSocket), so the bell / Alert Center update without waiting for
 * the next poll.
 * <p>
 * The boot application supplies the concrete implementation (a WebSocket fan-out
 * keyed by tenant). When no transport is configured, a no-op default stands in
 * so the service keeps working — the notifications are still persisted and will
 * surface on the next {@code GET /notifications}.
 */
public interface RealtimeNotifier {

    /**
     * Pushes an alert to every client currently connected for {@code tenantId}.
     * Best-effort: implementations must never throw back into the caller (a
     * push failure must not fail the persisted broadcast).
     *
     * @param tenantId     tenant whose connected clients should receive the push
     * @param notification a tenant-scoped view of the alert (userId is {@code null})
     */
    void publishAlert(UUID tenantId, NotificationView notification);
}
