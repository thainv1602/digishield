package com.digishield.analytics.application;

import com.digishield.contracts.events.PhishingReportConfirmedEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link PhishingReportConfirmedListener}.
 * <p>
 * The current skeleton body is a no-op; the test asserts the listener reacts to
 * the event without failing so the contract wiring stays intact.
 */
class PhishingReportConfirmedListenerTest {

    private final PhishingReportConfirmedListener listener = new PhishingReportConfirmedListener();

    @Test
    void on_whenEventReceived_handlesWithoutThrowing() {
        // Arrange
        PhishingReportConfirmedEvent event =
                new PhishingReportConfirmedEvent(UUID.randomUUID(), UUID.randomUUID());

        // Act + Assert
        assertThatCode(() -> listener.on(event)).doesNotThrowAnyException();
    }
}
