package com.digishield.learning.application;

import com.digishield.contracts.events.UserClickedSimulationEvent;
import com.digishield.learning.api.LearningService;
import com.digishield.learning.domain.PointAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(SimulationClickListener.class);

    private final LearningService learningService;
    private final PointsAwarder pointsAwarder;

    SimulationClickListener(LearningService learningService, PointsAwarder pointsAwarder) {
        this.learningService = learningService;
        this.pointsAwarder = pointsAwarder;
    }

    @ApplicationModuleListener
    void on(UserClickedSimulationEvent event) {
        // The click costs points whether or not there is a course to assign:
        // the score records what happened, and an empty catalogue is the
        // tenant's gap, not the user's.
        pointsAwarder.award(event.tenantId(), event.userId(), PointAction.SIMULATION_CLICKED);

        // A tenant with an empty catalogue has nothing to assign. Calling
        // autoEnroll anyway throws, and because these events are persistent and
        // retried, one click would fail forever rather than once — noisily, and
        // with nothing anybody can do about it until a course exists.
        if (learningService.listCourses(event.tenantId()).isEmpty()) {
            LOG.warn("No course in the catalogue for tenant {}; remediation not assigned to user {}",
                    event.tenantId(), event.userId());
            return;
        }
        learningService.autoEnroll(event.tenantId(), event.userId());
    }
}
