package com.digishield.notification.application;

import com.digishield.contracts.events.EnrollmentAssignedEvent;
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

    public EnrollmentNotificationListener(NotificationServiceImpl notificationService) {
        this.notificationService = notificationService;
    }

    @ApplicationModuleListener
    public void on(EnrollmentAssignedEvent event) {
        // Minimal body: create a reminder to complete the newly assigned course.
        UUID tenantId = event.tenantId();
        notificationService.createReminderForTenant(
                tenantId,
                event.userId(),
                "Bạn có khoá học mới",
                "Bạn vừa được gán khoá học " + event.courseId() + ". Hãy hoàn thành sớm nhé!");
    }
}
