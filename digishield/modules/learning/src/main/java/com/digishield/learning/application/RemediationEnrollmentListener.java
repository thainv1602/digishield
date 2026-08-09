package com.digishield.learning.application;

import com.digishield.contracts.events.RemediationEnrollmentRequestedEvent;
import com.digishield.learning.api.LearningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Auto-enrolls a user into remediation training when an AIDA orchestration run
 * flags them as at-risk (via {@code RemediationEnrollmentRequestedEvent} from the
 * analytics module).
 * <p>
 * Separate from {@link SimulationClickListener}: that reacts to a single
 * simulation click, this reacts to a batch risk recompute, so the two never
 * enroll off the same signal. {@link ApplicationModuleListener} gives
 * transactional, async, persistent cross-module handling; assigning a course
 * emits an {@code EnrollmentAssignedEvent} from {@link LearningService#autoEnroll}.
 */
@Component
class RemediationEnrollmentListener {

    private static final Logger LOG = LoggerFactory.getLogger(RemediationEnrollmentListener.class);

    private final LearningService learningService;

    RemediationEnrollmentListener(LearningService learningService) {
        this.learningService = learningService;
    }

    @ApplicationModuleListener
    void on(RemediationEnrollmentRequestedEvent event) {
        // Same reason as SimulationClickListener: autoEnroll throws on an empty
        // catalogue, and these events are persistent and retried, so one at-risk
        // user would fail repeatedly instead of once.
        if (learningService.listCourses(event.tenantId()).isEmpty()) {
            LOG.warn("No course in the catalogue for tenant {}; remediation not assigned to user {}",
                    event.tenantId(), event.userId());
            return;
        }
        learningService.autoEnroll(event.tenantId(), event.userId());
    }
}
