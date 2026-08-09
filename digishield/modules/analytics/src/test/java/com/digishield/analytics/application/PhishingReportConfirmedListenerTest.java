package com.digishield.analytics.application;

import com.digishield.analytics.api.AnalyticsService;
import com.digishield.contracts.events.PhishingReportConfirmedEvent;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PhishingReportConfirmedListener}.
 */
class PhishingReportConfirmedListenerTest {

    private final AnalyticsService analyticsService = mock(AnalyticsService.class);
    private final PhishingReportConfirmedListener listener =
            new PhishingReportConfirmedListener(analyticsService);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void onRecordsConfirmedReportForTheReporterAndClearsContext() {
        // Arrange
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PhishingReportConfirmedEvent event =
                new PhishingReportConfirmedEvent(tenantId, userId, UUID.randomUUID());

        // Act
        listener.on(event);

        // Assert: delegated to the service with the explicit tenant/user...
        verify(analyticsService).recordConfirmedReport(tenantId, userId);
        // ...and the tenant context was not leaked past the call.
        assertThat(TenantContext.get()).isNull();
    }
}
