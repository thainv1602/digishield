package com.digishield;

import com.digishield.notification.api.NotificationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Real {@link NotificationGateway} that delivers email via AWS SES (SDK v2).
 * Enabled only when {@code digishield.notifications.email.ses.enabled=true}; the
 * {@link RoutingNotificationGateway} routes EMAIL traffic here when present.
 * <p>
 * Credentials come from the default AWS provider chain (IRSA in the cluster).
 * Non-email channels are logged and skipped.
 */
@Component
@ConditionalOnProperty(name = "digishield.notifications.email.ses.enabled", havingValue = "true")
class SesEmailNotificationGateway implements NotificationGateway {

    private static final Logger LOG = LoggerFactory.getLogger(SesEmailNotificationGateway.class);

    private final SesV2Client sesClient;
    private final String fromAddress;

    SesEmailNotificationGateway(@Value("${digishield.notifications.email.from}") String fromAddress) {
        this.fromAddress = fromAddress;
        this.sesClient = SesV2Client.create();
    }

    @Override
    public void deliver(String channel, String recipient, String title, String body) {
        if (!"EMAIL".equals(channel)) {
            LOG.info("SES gateway skips non-email channel {} (recipient {})", channel, recipient);
            return;
        }
        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(fromAddress)
                .destination(Destination.builder().toAddresses(recipient).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(Content.builder().data(title == null ? "" : title).build())
                                .body(Body.builder()
                                        .text(Content.builder().data(body == null ? "" : body).build())
                                        .build())
                                .build())
                        .build())
                .build();
        sesClient.sendEmail(request);
        LOG.info("Sent email notification to {} via SES", recipient);
    }
}
