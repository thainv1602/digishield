package com.digishield;

import com.digishield.notification.api.NotificationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The single {@code @Primary} {@link NotificationGateway} injected into the
 * notification service. It routes each delivery to the channel-specific gateway —
 * EMAIL to the SES gateway, SMS to the SNS gateway. When that channel's gateway
 * is not enabled (or the channel is unknown) it logs the would-be delivery and
 * returns, preserving the dev behaviour of persisting without sending.
 * <p>
 * Routing here (rather than a single channel-aware gateway) lets email and SMS be
 * enabled independently, each backed by its own AWS client.
 */
@Component
@Primary
class RoutingNotificationGateway implements NotificationGateway {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingNotificationGateway.class);

    private final ObjectProvider<SesEmailNotificationGateway> emailGateway;
    private final ObjectProvider<SnsSmsNotificationGateway> smsGateway;

    RoutingNotificationGateway(ObjectProvider<SesEmailNotificationGateway> emailGateway,
                               ObjectProvider<SnsSmsNotificationGateway> smsGateway) {
        this.emailGateway = emailGateway;
        this.smsGateway = smsGateway;
    }

    @Override
    public void deliver(String channel, String recipient, String title, String body) {
        NotificationGateway gateway = switch (channel == null ? "" : channel) {
            case "EMAIL" -> emailGateway.getIfAvailable();
            case "SMS" -> smsGateway.getIfAvailable();
            default -> null;
        };
        if (gateway != null) {
            gateway.deliver(channel, recipient, title, body);
        } else {
            LOG.info("No gateway enabled for channel {} — '{}' to {} persisted but not sent",
                    channel, title, recipient);
        }
    }
}
