package com.digishield.notification.application;

import com.digishield.notification.api.NotificationGateway;
import com.digishield.notification.api.NotificationView;
import com.digishield.notification.api.RealtimeNotifier;
import com.digishield.notification.api.RecipientResolver;
import com.digishield.notification.api.UserDirectory;
import com.digishield.notification.domain.Notification;
import com.digishield.notification.domain.NotificationChannel;
import com.digishield.notification.domain.NotificationStatus;
import com.digishield.notification.domain.NotificationType;
import com.digishield.notification.infrastructure.NotificationRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationServiceImpl}.
 * <p>
 * Pure Mockito unit tests: no Spring context, no real database. The service reads
 * the tenant via {@code TenantContext.requireUuid()}, so the tenant id is treated as a UUID.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private NotificationRepository repository;

    @Mock
    private NotificationGateway gateway;

    @Mock
    private RecipientResolver recipients;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private RealtimeNotifier realtime;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID.toString());
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void send_deliversViaGatewayAndPersistsSentNotification() {
        // Arrange: email resolves and the gateway delivers successfully
        UUID userId = UUID.randomUUID();
        when(recipients.emailFor(userId)).thenReturn(Optional.of("user@example.com"));

        // Act
        Notification result = notificationService.send(
                userId, NotificationType.ALERT, NotificationChannel.EMAIL, "Subject", "Body");

        // Assert: delivered to the resolved address...
        verify(gateway).deliver("EMAIL", "user@example.com", "Subject", "Body");
        // ...and persisted as SENT
        verify(repository).save(notificationCaptor.capture());
        Notification persisted = notificationCaptor.getValue();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(persisted.getUserId()).isEqualTo(userId);
        assertThat(persisted.getType()).isEqualTo(NotificationType.ALERT);
        assertThat(persisted.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(persisted.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(result).isSameAs(persisted);
    }

    @Test
    void send_smsChannel_resolvesPhoneAndDelivers() {
        // Arrange: SMS uses the phone address, not the email
        UUID userId = UUID.randomUUID();
        when(recipients.phoneFor(userId)).thenReturn(Optional.of("+84901234561"));

        // Act
        notificationService.send(userId, NotificationType.ALERT, NotificationChannel.SMS, "Subject", "Body");

        // Assert: delivered to the phone via the SMS channel, persisted SENT
        verify(gateway).deliver("SMS", "+84901234561", "Subject", "Body");
        verify(recipients, org.mockito.Mockito.never()).emailFor(userId);
        verify(repository).save(notificationCaptor.capture());
        Notification persisted = notificationCaptor.getValue();
        assertThat(persisted.getChannel()).isEqualTo(NotificationChannel.SMS);
        assertThat(persisted.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void send_whenRecipientUnresolved_persistsFailedAndSkipsGateway() {
        // Arrange: no email for the user
        UUID userId = UUID.randomUUID();
        when(recipients.emailFor(userId)).thenReturn(Optional.empty());

        // Act
        notificationService.send(userId, NotificationType.ALERT, NotificationChannel.EMAIL, "S", "B");

        // Assert
        verifyNoInteractions(gateway);
        verify(repository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void send_whenGatewayThrows_persistsFailed() {
        // Arrange: resolves, but the gateway delivery fails
        UUID userId = UUID.randomUUID();
        when(recipients.emailFor(userId)).thenReturn(Optional.of("user@example.com"));
        doThrow(new RuntimeException("ses down"))
                .when(gateway).deliver(eq("EMAIL"), anyString(), anyString(), anyString());

        // Act
        notificationService.send(userId, NotificationType.ALERT, NotificationChannel.EMAIL, "S", "B");

        // Assert
        verify(repository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void scheduleReminder_persistsScheduledReminderOverInAppChannel() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act
        notificationService.scheduleReminder(userId, "Remember", "Finish your course");

        // Assert
        verify(repository).save(notificationCaptor.capture());
        Notification persisted = notificationCaptor.getValue();
        assertThat(persisted.getType()).isEqualTo(NotificationType.REMINDER);
        assertThat(persisted.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(persisted.getStatus()).isEqualTo(NotificationStatus.SCHEDULED);
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(persisted.getUserId()).isEqualTo(userId);
    }

    @Test
    void broadcastAlert_fansOutToEveryTenantUser() {
        // Arrange: two users in the tenant
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        when(userDirectory.allUserIds()).thenReturn(java.util.List.of(u1, u2));

        // Act
        var created = notificationService.broadcastAlert("[CRITICAL] Heads up", "Risk detected");

        // Assert: one in-app ALERT persisted per user
        assertThat(created).hasSize(2);
        verify(repository, org.mockito.Mockito.times(2)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
                .allSatisfy(n -> {
                    assertThat(n.getType()).isEqualTo(NotificationType.ALERT);
                    assertThat(n.getChannel()).isEqualTo(NotificationChannel.IN_APP);
                    assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
                    assertThat(n.getTitle()).isEqualTo("[CRITICAL] Heads up");
                })
                .extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(u1, u2);

        // ...and pushed once to the tenant's connected clients (tenant-scoped view)
        ArgumentCaptor<NotificationView> viewCaptor = ArgumentCaptor.forClass(NotificationView.class);
        verify(realtime).publishAlert(eq(TENANT_ID), viewCaptor.capture());
        NotificationView pushed = viewCaptor.getValue();
        assertThat(pushed.userId()).isNull();
        assertThat(pushed.type()).isEqualTo("alert");
        assertThat(pushed.title()).isEqualTo("[CRITICAL] Heads up");
    }
}
