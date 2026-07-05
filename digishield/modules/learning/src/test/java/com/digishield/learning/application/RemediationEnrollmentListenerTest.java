package com.digishield.learning.application;

import com.digishield.contracts.events.RemediationEnrollmentRequestedEvent;
import com.digishield.learning.api.LearningService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link RemediationEnrollmentListener}.
 */
class RemediationEnrollmentListenerTest {

    private final LearningService learningService = mock(LearningService.class);
    private final RemediationEnrollmentListener listener =
            new RemediationEnrollmentListener(learningService);

    @Test
    void on_autoEnrollsTheAtRiskUser() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        listener.on(new RemediationEnrollmentRequestedEvent(tenantId, userId, UUID.randomUUID()));

        verify(learningService).autoEnroll(tenantId, userId);
    }
}
