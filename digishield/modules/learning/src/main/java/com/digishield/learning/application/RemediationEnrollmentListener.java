package com.digishield.learning.application;

import com.digishield.contracts.events.RemediationEnrollmentRequestedEvent;
import com.digishield.learning.api.LearningService;
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

    private final LearningService learningService;

    RemediationEnrollmentListener(LearningService learningService) {
        this.learningService = learningService;
    }

    @ApplicationModuleListener
    void on(RemediationEnrollmentRequestedEvent event) {
        learningService.autoEnroll(event.tenantId(), event.userId());
    }
}
