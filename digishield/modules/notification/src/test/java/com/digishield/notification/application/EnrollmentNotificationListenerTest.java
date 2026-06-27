package com.digishield.notification.application;

import com.digishield.contracts.events.EnrollmentAssignedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link EnrollmentNotificationListener}.
 * <p>
 * Pure Mockito unit tests: the delegate service is mocked so we can assert the
 * listener turns an {@link EnrollmentAssignedEvent} into a REMINDER for the tenant.
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentNotificationListenerTest {

    @Mock
    private NotificationServiceImpl notificationService;

    @InjectMocks
    private EnrollmentNotificationListener listener;

    @Captor
    private ArgumentCaptor<String> bodyCaptor;

    @Test
    void on_whenEnrollmentAssigned_createsReminderForResolvedTenantAndUser() {
        // Arrange
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        EnrollmentAssignedEvent event =
                new EnrollmentAssignedEvent(tenantId, userId, courseId);

        // Act
        listener.on(event);

        // Assert
        verify(notificationService).createReminderForTenant(
                eq(tenantId), eq(userId), eq("Bạn có khoá học mới"), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains(courseId.toString());
    }
}
