package com.digishield.learning.application;

import com.digishield.contracts.events.UserClickedSimulationEvent;
import com.digishield.learning.api.LearningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link SimulationClickListener}.
 * <p>
 * Verifies that receiving a {@link UserClickedSimulationEvent} triggers the
 * learning service to auto-enroll the clicking user.
 */
@ExtendWith(MockitoExtension.class)
class SimulationClickListenerTest {

    @Mock
    private LearningService learningService;

    @InjectMocks
    private SimulationClickListener listener;

    @Test
    void on_whenUserClickedSimulation_callsAutoEnrollWithEventFields() {
        // Arrange
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UserClickedSimulationEvent event =
                new UserClickedSimulationEvent(tenantId, userId, campaignId);

        org.mockito.Mockito.when(learningService.listCourses(tenantId))
                .thenReturn(java.util.List.of(new com.digishield.learning.api.CourseView(java.util.UUID.randomUUID(), null, "Khoá", "basic", "vi", 30, 1, null, null)));

        // Act
        listener.on(event);

        // Assert: the listener delegates to the learning service for auto-enrollment
        verify(learningService).autoEnroll(tenantId, userId);
    }

    @Test
    void on_whenCatalogueIsEmpty_skipsInsteadOfFailingForever() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.randomUUID();
        org.mockito.Mockito.when(learningService.listCourses(tenantId))
                .thenReturn(java.util.List.of());

        listener.on(new UserClickedSimulationEvent(tenantId, userId, UUID.randomUUID()));

        // autoEnroll throws with no course, and these events are persistent and
        // retried — so calling it anyway would fail on every redelivery rather
        // than once.
        verify(learningService, org.mockito.Mockito.never()).autoEnroll(tenantId, userId);
    }
}
