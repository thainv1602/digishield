package com.digishield.reporting.application;

import com.digishield.contracts.events.PhishingReportConfirmedEvent;
import com.digishield.reporting.domain.AiLabel;
import com.digishield.reporting.domain.PhishingReport;
import com.digishield.reporting.domain.ReportStatus;
import com.digishield.reporting.infrastructure.BlacklistEntryRepository;
import com.digishield.reporting.infrastructure.PhishingReportRepository;
import com.digishield.shared.messaging.EventPublisher;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReportingServiceImpl}.
 * <p>
 * Pure Mockito unit tests: no Spring context, no real database.
 */
@ExtendWith(MockitoExtension.class)
class ReportingServiceImplTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private PhishingReportRepository reportRepository;

    @Mock
    private BlacklistEntryRepository blacklistRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ReportingServiceImpl reportingService;

    @Captor
    private ArgumentCaptor<PhishingReport> reportCaptor;

    @Captor
    private ArgumentCaptor<PhishingReportConfirmedEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void submit_persistsReportWithSubmittedStatusForCurrentTenant() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(reportRepository.save(any(PhishingReport.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        PhishingReport result = reportingService.submit(userId, "suspicious email body");

        // Assert
        verify(reportRepository).save(reportCaptor.capture());
        PhishingReport persisted = reportCaptor.getValue();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(persisted.getUserId()).isEqualTo(userId);
        assertThat(persisted.getPayload()).isEqualTo("suspicious email body");
        assertThat(persisted.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(persisted.getAiLabel()).isNull();
        assertThat(persisted.getAiConfidence()).isEqualTo(0.0);
        assertThat(result).isSameAs(persisted);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void triage_whenConfirmingThreat_marksConfirmedAndPublishesEvent() {
        // Arrange
        UUID reportId = UUID.randomUUID();
        PhishingReport report = new PhishingReport(
                reportId, TENANT_ID, UUID.randomUUID(), "payload",
                null, 0.0, ReportStatus.SUBMITTED);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(PhishingReport.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        PhishingReport result = reportingService.triage(reportId, true);

        // Assert
        assertThat(result.getStatus()).isEqualTo(ReportStatus.CONFIRMED);
        assertThat(result.getAiLabel()).isEqualTo(AiLabel.THREAT);

        verify(eventPublisher).publish(eventCaptor.capture());
        PhishingReportConfirmedEvent event = eventCaptor.getValue();
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.reportId()).isEqualTo(reportId);
    }

    @Test
    void triage_whenNotConfirmingThreat_dismissesAndDoesNotPublish() {
        // Arrange
        UUID reportId = UUID.randomUUID();
        PhishingReport report = new PhishingReport(
                reportId, TENANT_ID, UUID.randomUUID(), "payload",
                null, 0.0, ReportStatus.SUBMITTED);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(PhishingReport.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        PhishingReport result = reportingService.triage(reportId, false);

        // Assert
        assertThat(result.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(result.getAiLabel()).isEqualTo(AiLabel.CLEAN);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void triage_whenReportNotFound_throwsAndPublishesNothing() {
        // Arrange
        UUID missingId = UUID.randomUUID();
        when(reportRepository.findById(missingId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> reportingService.triage(missingId, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(missingId.toString());

        verify(reportRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
