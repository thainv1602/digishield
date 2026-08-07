package com.digishield.notification.application;

import com.digishield.contracts.events.EnrollmentDueEvent;
import com.digishield.shared.tenantcontext.Messages;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns the learning module's due sweep into a reminder the learner sees.
 *
 * <p>Two wordings, chosen by the event rather than recomputed here: one for an
 * assignment coming due, one for an assignment already late. Deciding it twice
 * is how the message ends up telling someone they are late while the portal
 * still shows a day left.
 */
@Component
public class EnrollmentDueListener {

    private final NotificationServiceImpl notificationService;
    private final Messages messages;

    public EnrollmentDueListener(NotificationServiceImpl notificationService, Messages messages) {
        this.notificationService = notificationService;
        this.messages = messages;
    }

    @ApplicationModuleListener
    public void on(EnrollmentDueEvent event) {
        String key = event.overdue() ? "notification.enrollment.overdue" : "notification.enrollment.due";
        notificationService.createReminderForTenant(
                event.tenantId(),
                event.userId(),
                messages.get(key + ".title"),
                messages.get(key + ".body", event.dueAt()));
    }
}
