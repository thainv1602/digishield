package com.digishield.notification.application;

import com.digishield.contracts.events.SimulationDeliveryRequestedEvent;
import com.digishield.notification.api.NotificationService;
import com.digishield.notification.domain.NotificationChannel;
import com.digishield.notification.domain.NotificationType;
import com.digishield.shared.tenantcontext.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SimulationDeliveryListener}.
 * <p>
 * The point of these: a launched campaign must deliver the <em>authored</em>
 * template, not a generic notice, and must not deliver over a channel that has
 * no transport. Pure Mockito — the notification service is mocked and the sent
 * title/body captured.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SimulationDeliveryListenerTest {

    private static final String BASE_URL = "https://digishield.example";
    private static final String TRACK_PATH = "/api/v1/sim/track/abc";
    private static final String LINK = BASE_URL + TRACK_PATH;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Messages messages;

    private SimulationDeliveryListener listener;

    @BeforeEach
    void setUp() {
        when(messages.get(eq("simulation.delivery.fallback.subject"))).thenReturn("Generic subject");
        when(messages.get(eq("simulation.delivery.fallback.body"), any()))
                .thenReturn("Generic body: " + LINK);
        listener = new SimulationDeliveryListener(notificationService, messages, BASE_URL);
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("running inline leaves the caller's tenant intact")
    void doesNotStealTheCallersTenantWhenItRunsInline() {
        // @Async is inert here, as it is in any plain unit or slice context, so
        // the listener runs on the caller's thread. It used to clear the
        // ThreadLocal in its finally block, which took the caller's tenant with
        // it and broke the very campaign send that published the event.
        String caller = "11111111-1111-1111-1111-111111111111";
        com.digishield.shared.tenantcontext.TenantContext.set(caller);
        try {
            listener.on(event("EMAIL", "Subject", "Body {{link}}", "html"));

            assertThat(com.digishield.shared.tenantcontext.TenantContext.get())
                    .isEqualTo(caller);
        } finally {
            com.digishield.shared.tenantcontext.TenantContext.clear();
        }
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("on a thread with no tenant, it leaves none behind")
    void leavesNothingBehindOnItsOwnThread() {
        listener.on(event("EMAIL", "Subject", "Body {{link}}", "html"));

        assertThat(com.digishield.shared.tenantcontext.TenantContext.get()).isNull();
    }

    private static SimulationDeliveryRequestedEvent event(
            String channel, String subject, String body, String bodyFormat) {
        return new SimulationDeliveryRequestedEvent(
                UUID.randomUUID(), UUID.randomUUID(), channel, subject, body, bodyFormat,
                java.util.List.of(new SimulationDeliveryRequestedEvent.Recipient(
                        UUID.randomUUID(), TRACK_PATH)));
    }

    private Sent capture() {
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationChannel> channel = ArgumentCaptor.forClass(NotificationChannel.class);
        verify(notificationService).send(
                any(UUID.class), eq(NotificationType.SYSTEM), channel.capture(),
                title.capture(), body.capture());
        return new Sent(channel.getValue(), title.getValue(), body.getValue());
    }

    private record Sent(NotificationChannel channel, String title, String body) {
    }

    @Test
    void sendsTheTemplateSubjectAndSubstitutesTheLinkPlaceholder() {
        listener.on(event("EMAIL", "[Thuế] Hoàn thuế 2025",
                "Xác nhận tại {{link}} trước 30/07.", "text"));

        Sent sent = capture();
        assertThat(sent.channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(sent.title()).isEqualTo("[Thuế] Hoàn thuế 2025");
        assertThat(sent.body()).isEqualTo("Xác nhận tại " + LINK + " trước 30/07.");
        assertThat(sent.body()).doesNotContain("{{link}}");
    }

    @Test
    void appendsTheLinkWhenTheTemplateHasNoPlaceholder() {
        listener.on(event("EMAIL", "Subject", "Nội dung không có placeholder.", "text"));

        Sent sent = capture();
        assertThat(sent.body()).startsWith("Nội dung không có placeholder.");
        assertThat(sent.body()).endsWith(LINK);
    }

    @Test
    void stripsHtmlWhenDeliveringOverSms() {
        listener.on(event("SMS", "Cảnh báo",
                "<div><p>Tài khoản bị khoá.</p><a href=\"{{link}}\">Xác minh</a></div>", "html"));

        Sent sent = capture();
        assertThat(sent.channel()).isEqualTo(NotificationChannel.SMS);
        assertThat(sent.body()).doesNotContain("<").doesNotContain(">");
        assertThat(sent.body()).contains("Tài khoản bị khoá.").contains(LINK);
    }

    @Test
    void fallsBackToTheGenericMessageWhenTheCampaignHasNoTemplate() {
        listener.on(event("EMAIL", null, null, null));

        Sent sent = capture();
        assertThat(sent.title()).isEqualTo("Generic subject");
        assertThat(sent.body()).isEqualTo("Generic body: " + LINK);
    }

    @Test
    void deliversAQrCampaignOverEmail() {
        listener.on(event("QR", "Quét mã", "Quét: {{link}}", "text"));

        assertThat(capture().channel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    void doesNotDeliverOverAChannelThatHasNoTransport() {
        listener.on(event("ZALO", "Tin nhắn Zalo", "Nội dung {{link}}", "text"));

        // Silently emailing a "Zalo" campaign would misreport what was sent.
        verify(notificationService, never()).send(
                any(UUID.class), any(NotificationType.class), any(NotificationChannel.class),
                anyString(), anyString());
    }
}
