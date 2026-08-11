package com.digishield.notification.application;

import com.digishield.contracts.events.SimulationDeliveryRequestedEvent;
import com.digishield.notification.api.NotificationService;
import com.digishield.notification.domain.NotificationChannel;
import com.digishield.notification.domain.NotificationType;
import com.digishield.shared.tenantcontext.Messages;
import com.digishield.shared.tenantcontext.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Delivers a launched simulation campaign's tracking link to each recipient over
 * the campaign channel. Listens for {@link SimulationDeliveryRequestedEvent} and
 * routes through {@link NotificationService}, which resolves the recipient's
 * email/phone and hands off to the configured gateway (real AWS SES/SNS when
 * enabled, otherwise the logging gateway — the send is still recorded).
 *
 * <p>The message is the campaign template's authored subject and body, with the
 * recipient's tracking URL substituted for the {@code {{link}}} placeholder (or
 * appended when the template has none). Campaigns without a resolvable template
 * fall back to a generic notice so a launch never silently sends nothing.
 *
 * <p>Runs asynchronously in its own transaction ({@link ApplicationModuleListener}),
 * so it sets the tenant on the current thread before touching tenant-scoped data.
 */
@Component
class SimulationDeliveryListener {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationDeliveryListener.class);

    /** Placeholder a template body uses to position the tracking link. */
    private static final String LINK_PLACEHOLDER = "{{link}}";

    private final NotificationService notificationService;
    private final Messages messages;
    /** Absolute public base URL prepended to the tracking path in the message. */
    private final String publicBaseUrl;

    SimulationDeliveryListener(NotificationService notificationService,
                               Messages messages,
                               @Value("${digishield.app.public-base-url:}") String publicBaseUrl) {
        this.notificationService = notificationService;
        this.messages = messages;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    @ApplicationModuleListener
    void on(SimulationDeliveryRequestedEvent event) {
        String previousTenant = TenantContext.get();
        TenantContext.set(event.tenantId().toString());
        try {
            NotificationChannel channel = toChannel(event.channel());
            if (channel == null) {
                // No transport for this channel — do not quietly deliver over the
                // wrong one. The campaign still records DELIVERED/click tracking.
                LOG.warn("Campaign {} uses channel {}, which has no delivery transport; "
                                + "skipping the send for {} recipient(s)",
                        event.campaignId(), event.channel(), event.recipients().size());
                return;
            }

            String title = StringUtils.hasText(event.subject())
                    ? event.subject()
                    : messages.get("simulation.delivery.fallback.subject");

            // The event now carries the whole audience, so one failure must still
            // cost only its own recipient: catch per person, not per campaign.
            for (SimulationDeliveryRequestedEvent.Recipient recipient : event.recipients()) {
                try {
                    String link = publicBaseUrl + recipient.trackPath();
                    String body = renderBody(event, link, channel);
                    notificationService.send(
                            recipient.userId(), NotificationType.SYSTEM, channel, title, body);
                } catch (Exception e) {
                    // Never let one delivery failure break the campaign launch.
                    LOG.warn("Simulation delivery for user {} failed: {}",
                            recipient.userId(), e.toString());
                }
            }
        } finally {
            TenantContext.restore(previousTenant);
        }
    }

    /**
     * Builds the outgoing body: the template body with the tracking link woven in,
     * or the generic fallback when the campaign carries no template.
     */
    private String renderBody(SimulationDeliveryRequestedEvent event, String link, NotificationChannel channel) {
        if (!StringUtils.hasText(event.body())) {
            return messages.get("simulation.delivery.fallback.body", link);
        }
        String body = event.body();
        // SMS carries no markup: an HTML lure would arrive as raw tags.
        if (channel == NotificationChannel.SMS && isHtml(event.bodyFormat())) {
            body = stripHtml(body);
        }
        return body.contains(LINK_PLACEHOLDER)
                ? body.replace(LINK_PLACEHOLDER, link)
                : body + System.lineSeparator() + link;
    }

    private static boolean isHtml(String bodyFormat) {
        return "html".equalsIgnoreCase(bodyFormat);
    }

    /** Crude tag strip — enough to turn an HTML lure into readable SMS text. */
    private static String stripHtml(String html) {
        return html.replaceAll("(?is)<(script|style).*?</\\1>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    /**
     * Maps a simulation channel to a deliverable notification channel, or
     * {@code null} when the channel has no transport yet.
     *
     * <p>QR is deliberately delivered over email: the quishing lure is an email
     * carrying the QR image, and the operator also gets a rendered QR per
     * recipient in the send-result panel.
     */
    private static NotificationChannel toChannel(String simChannel) {
        if (!StringUtils.hasText(simChannel)) {
            return NotificationChannel.EMAIL;
        }
        return switch (simChannel.toUpperCase()) {
            case "SMS" -> NotificationChannel.SMS;
            case "EMAIL", "QR" -> NotificationChannel.EMAIL;
            // ZALO, TEAMS, SLACK, USB, VOICE: no gateway exists for these.
            default -> null;
        };
    }
}
