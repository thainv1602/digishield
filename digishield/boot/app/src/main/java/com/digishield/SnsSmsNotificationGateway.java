package com.digishield;

import com.digishield.notification.api.NotificationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Real {@link NotificationGateway} that delivers SMS via AWS SNS (SDK v2).
 * Enabled only when {@code digishield.notifications.sms.sns.enabled=true}; the
 * {@link RoutingNotificationGateway} routes SMS traffic here when present.
 * <p>
 * Credentials come from the default AWS provider chain (IRSA in the cluster).
 * Messages are sent as {@code Transactional} (higher delivery priority); an
 * optional sender id is attached when configured and supported in the region.
 * Non-SMS channels are logged and skipped.
 */
@Component
@ConditionalOnProperty(name = "digishield.notifications.sms.sns.enabled", havingValue = "true")
class SnsSmsNotificationGateway implements NotificationGateway {

    private static final Logger LOG = LoggerFactory.getLogger(SnsSmsNotificationGateway.class);

    private final SnsClient snsClient;
    private final String senderId;

    SnsSmsNotificationGateway(@Value("${digishield.notifications.sms.sender-id:}") String senderId) {
        this.senderId = senderId;
        this.snsClient = SnsClient.create();
    }

    @Override
    public void deliver(String channel, String recipient, String title, String body) {
        if (!"SMS".equals(channel)) {
            LOG.info("SNS gateway skips non-SMS channel {} (recipient {})", channel, recipient);
            return;
        }
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        attributes.put("AWS.SNS.SMS.SMSType", MessageAttributeValue.builder()
                .dataType("String").stringValue("Transactional").build());
        if (StringUtils.hasText(senderId)) {
            attributes.put("AWS.SNS.SMS.SenderID", MessageAttributeValue.builder()
                    .dataType("String").stringValue(senderId).build());
        }
        snsClient.publish(PublishRequest.builder()
                .phoneNumber(recipient)
                .message(compose(title, body))
                .messageAttributes(attributes)
                .build());
        LOG.info("Sent SMS notification to {} via SNS", recipient);
    }

    /** Combines subject and body into a single plain-text SMS. */
    private static String compose(String title, String body) {
        boolean hasTitle = StringUtils.hasText(title);
        boolean hasBody = StringUtils.hasText(body);
        if (hasTitle && hasBody) {
            return title + ": " + body;
        }
        return hasTitle ? title : (hasBody ? body : "");
    }
}
