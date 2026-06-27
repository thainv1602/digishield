package com.digishield.notification.application;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    void send_persistsSentNotificationWithGivenTypeAndChannel() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act
        Notification result = notificationService.send(
                userId, NotificationType.ALERT, NotificationChannel.EMAIL, "Subject", "Body");

        // Assert
        verify(repository).save(notificationCaptor.capture());
        Notification persisted = notificationCaptor.getValue();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(persisted.getUserId()).isEqualTo(userId);
        assertThat(persisted.getType()).isEqualTo(NotificationType.ALERT);
        assertThat(persisted.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(persisted.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(persisted.getTitle()).isEqualTo("Subject");
        assertThat(persisted.getBody()).isEqualTo("Body");
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(result).isSameAs(persisted);
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
    void broadcastAlert_persistsSentAlertOverInAppChannel() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act
        notificationService.broadcastAlert(userId, "Heads up", "Risk detected");

        // Assert
        verify(repository).save(notificationCaptor.capture());
        Notification persisted = notificationCaptor.getValue();
        assertThat(persisted.getType()).isEqualTo(NotificationType.ALERT);
        assertThat(persisted.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(persisted.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(persisted.getTitle()).isEqualTo("Heads up");
        assertThat(persisted.getBody()).isEqualTo("Risk detected");
    }
}
