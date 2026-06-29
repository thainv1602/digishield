package com.digishield.notification.application;

import com.digishield.notification.api.NotificationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link NotificationGateway} used when no real gateway (e.g. AWS SES)
 * is configured. It logs the would-be delivery and succeeds, preserving the dev
 * behaviour where notifications are persisted but nothing is actually sent.
 * <p>
 * When the boot app's SES gateway is enabled it is registered as
 * {@code @Primary}, so it is preferred over this default for injection.
 */
@Component
public class LoggingNotificationGateway implements NotificationGateway {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingNotificationGateway.class);

    @Override
    public void deliver(String channel, String recipient, String title, String body) {
        LOG.info("No external notification gateway configured — would deliver {} to {}: {}",
                channel, recipient, title);
    }
}
