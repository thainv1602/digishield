package com.digishield;

import com.digishield.learning.api.LearningService;
import com.digishield.shared.tenantcontext.TenantContext;
import com.digishield.simulation.api.RemediationStatusProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wires the simulation module's {@link RemediationStatusProvider} SPI to the
 * learning module, closing the loop a click opens: clicking enrols the person in
 * remediation, and the campaign's results table reports how far they got.
 */
@Component
class LearningRemediationStatus implements RemediationStatusProvider {

    private static final String COMPLETED = "completed";

    private final LearningService learningService;

    LearningRemediationStatus(LearningService learningService) {
        this.learningService = learningService;
    }

    @Override
    public Map<UUID, Remediation> statusByUser() {
        UUID tenantId = TenantContext.requireUuid();
        Map<UUID, Remediation> byUser = new HashMap<>();
        for (var enrolment : learningService.listEnrollments(tenantId, null)) {
            if (enrolment.userId() == null) {
                continue;
            }
            Remediation state = COMPLETED.equalsIgnoreCase(enrolment.status())
                    ? Remediation.COMPLETED
                    : Remediation.IN_PROGRESS;
            // Someone enrolled more than once counts as finished if any of it is:
            // the point is whether they have done the training, not how many
            // times they were assigned it.
            byUser.merge(enrolment.userId(), state,
                    (a, b) -> a == Remediation.COMPLETED || b == Remediation.COMPLETED
                            ? Remediation.COMPLETED : Remediation.IN_PROGRESS);
        }
        return byUser;
    }
}
