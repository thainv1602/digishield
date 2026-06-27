package com.digishield.learning.application;

import com.digishield.contracts.events.UserClickedSimulationEvent;
import com.digishield.learning.api.LearningService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens for the event of a user clicking a phishing simulation link and
 * automatically assigns them a supplementary training course.
 * <p>
 * {@link ApplicationModuleListener} ensures transactional, async, and persistent
 * processing between Modulith modules.
 * Assigning a course emits an {@code EnrollmentAssignedEvent}
 * (via {@code ApplicationEventPublisher} inside {@link LearningService#assign}).
 */
@Component
class SimulationClickListener {

    private final LearningService learningService;

    SimulationClickListener(LearningService learningService) {
        this.learningService = learningService;
    }

    @ApplicationModuleListener
    void on(UserClickedSimulationEvent event) {
        learningService.autoEnroll(event.tenantId(), event.userId());
    }
}
