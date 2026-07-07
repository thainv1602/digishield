package com.digishield.notification.application;

import com.digishield.contracts.events.EnrollmentAssignedEvent;
import com.digishield.shared.tenantcontext.Messages;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens to the {@link EnrollmentAssignedEvent} event to create a reminder
 * notification (REMINDER) for the user who was just assigned a course.
 */
@Component
public class EnrollmentNotificationListener {

    private final NotificationServiceImpl notificationService;
    private final Messages messages;

    public EnrollmentNotificationListener(NotificationServiceImpl notificationService, Messages messages) {
        this.notificationService = notificationService;
        this.messages = messages;
    }

    @ApplicationModuleListener
    public void on(EnrollmentAssignedEvent event) {
        // Minimal body: create a reminder to complete the newly assigned course.
        UUID tenantId = event.tenantId();
        notificationService.createReminderForTenant(
                tenantId,
                event.userId(),
                messages.get("notification.enrollment.title"),
                messages.get("notification.enrollment.body", event.courseId()));
    }
}
