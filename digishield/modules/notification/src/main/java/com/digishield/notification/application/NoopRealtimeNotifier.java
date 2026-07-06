package com.digishield.notification.application;

import com.digishield.notification.api.NotificationView;
import com.digishield.notification.api.RealtimeNotifier;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link RealtimeNotifier} used when no real-time transport is wired.
 * <p>
 * Logs at debug and drops the push — the notification is already persisted, so
 * clients pick it up on their next poll. The boot application overrides this
 * with a {@code @Primary} WebSocket-backed implementation.
 */
@Component
public class NoopRealtimeNotifier implements RealtimeNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(NoopRealtimeNotifier.class);

    @Override
    public void publishAlert(UUID tenantId, NotificationView notification) {
        LOG.debug("No real-time transport configured; skipping push of alert {} to tenant {}",
                notification != null ? notification.id() : null, tenantId);
    }
}
