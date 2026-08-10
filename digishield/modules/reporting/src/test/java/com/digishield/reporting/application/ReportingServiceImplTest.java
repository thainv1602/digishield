package com.digishield.reporting.application;

import com.digishield.shared.tenantcontext.AuditRecorder;
import com.digishield.contracts.events.PhishingReportConfirmedEvent;
import com.digishield.reporting.api.TriageDecision;
import com.digishield.reporting.api.dto.PhishingReportDto;
import com.digishield.reporting.domain.BlacklistEntry;
import com.digishield.reporting.domain.BlacklistType;
import com.digishield.reporting.domain.AiLabel;
import com.digishield.reporting.domain.PhishingReport;
import com.digishield.reporting.domain.ReportStatus;
import com.digishield.reporting.infrastructure.BlacklistEntryRepository;
import com.digishield.reporting.infrastructure.PhishingReportRepository;
import com.digishield.reporting.infrastructure.ThreatIntelRepository;
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
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
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
    private ThreatIntelRepository threatIntelRepository;

    @Mock
    private EventPublisher eventPublisher;

    /** The audit sink is optional; a mock provider yields none, so calls are no-ops. */

    @Mock

    private ObjectProvider<AuditRecorder> auditRecorder;

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
    void submitPersistsReportWithSubmittedStatusForCurrentTenant() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(reportRepository.save(any(PhishingReport.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        PhishingReportDto result = reportingService.submit(userId, "suspicious email body", "email");

        // Assert
        verify(reportRepository).save(reportCaptor.capture());
        PhishingReport persisted = reportCaptor.getValue();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(persisted.getUserId()).isEqualTo(userId);
        assertThat(persisted.getPayload()).isEqualTo("suspicious email body");
        assertThat(persisted.getChannel()).isEqualTo("email");
        assertThat(persisted.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(persisted.getAiLabel()).isNull();
        assertThat(persisted.getAiConfidence()).isEqualTo(0.0);
        // The caller gets a DTO describing that row, not the entity itself:
        // the JPA object never crosses the HTTP boundary.
        assertThat(result.id()).isEqualTo(persisted.getId());
        assertThat(result.status()).isEqualTo("submitted");
        assertThat(result.channel()).isEqualTo("email");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void triageWhenConfirmingThreatMarksConfirmedAndPublishesEvent() {
        // Arrange
        UUID reportId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        PhishingReport report = new PhishingReport(
                reportId, TENANT_ID, reporterId, "payload",
                null, 0.0, ReportStatus.SUBMITTED);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(PhishingReport.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        PhishingReportDto result = reportingService.triage(reportId, TriageDecision.CONFIRM_THREAT, false);

        // Assert: what is persisted, and what the client is told
        verify(reportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getStatus()).isEqualTo(ReportStatus.CONFIRMED);
        assertThat(reportCaptor.getValue().getAiLabel()).isEqualTo(AiLabel.THREAT);
        assertThat(result.status()).isEqualTo("confirmed");
        assertThat(result.aiLabel()).isEqualTo("threat");

        verify(eventPublisher).publish(eventCaptor.capture());
        PhishingReportConfirmedEvent event = eventCaptor.getValue();
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.userId()).isEqualTo(reporterId);
        assertThat(event.reportId()).isEqualTo(reportId);
    }

    @Test
    void triageWhenDismissingMarksDismissedAndDoesNotPublish() {
        // Arrange
        UUID reportId = UUID.randomUUID();
        PhishingReport report = new PhishingReport(
                reportId, TENANT_ID, UUID.randomUUID(), "payload",
                null, 0.0, ReportStatus.SUBMITTED);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(PhishingReport.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        PhishingReportDto result = reportingService.triage(reportId, TriageDecision.DISMISS, false);

        // Assert
        verify(reportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(reportCaptor.getValue().getAiLabel()).isEqualTo(AiLabel.CLEAN);
        assertThat(result.status()).isEqualTo("dismissed");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void triageWhenQuarantiningMarksThreatButPublishesNoEvent() {
        // Arrange
        UUID reportId = UUID.randomUUID();
        PhishingReport report = new PhishingReport(
                reportId, TENANT_ID, UUID.randomUUID(), "payload",
                null, 0.0, ReportStatus.SUBMITTED);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(PhishingReport.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        PhishingReportDto result = reportingService.triage(reportId, TriageDecision.QUARANTINE, false);

        // Assert: judged a threat, but the verdict is not final
        verify(reportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getStatus()).isEqualTo(ReportStatus.QUARANTINED);
        assertThat(reportCaptor.getValue().getAiLabel()).isEqualTo(AiLabel.THREAT);
        assertThat(result.status()).isEqualTo("quarantined");
        assertThat(result.aiLabel()).isEqualTo("threat");

        // The reporter must not be rewarded twice: no event until it is confirmed.
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void triageQuarantineIsDistinctFromDismissEvenThoughNeitherPublishes() {
        // Both are "not confirmed", which the old boolean could not tell apart:
        // anything that was not confirm became a dismissal.
        UUID quarantinedId = UUID.randomUUID();
        UUID dismissedId = UUID.randomUUID();
        when(reportRepository.findById(quarantinedId)).thenReturn(Optional.of(new PhishingReport(
                quarantinedId, TENANT_ID, UUID.randomUUID(), "payload",
                null, 0.0, ReportStatus.SUBMITTED)));
        when(reportRepository.findById(dismissedId)).thenReturn(Optional.of(new PhishingReport(
                dismissedId, TENANT_ID, UUID.randomUUID(), "payload",
                null, 0.0, ReportStatus.SUBMITTED)));
        when(reportRepository.save(any(PhishingReport.class))).thenAnswer(inv -> inv.getArgument(0));

        PhishingReportDto quarantined =
                reportingService.triage(quarantinedId, TriageDecision.QUARANTINE, false);
        PhishingReportDto dismissed =
                reportingService.triage(dismissedId, TriageDecision.DISMISS, false);

        assertThat(quarantined.status()).isNotEqualTo(dismissed.status());
        assertThat(quarantined.aiLabel()).isEqualTo("threat");
        assertThat(dismissed.aiLabel()).isEqualTo("clean");
    }

    /** Builds a report that carries a sender, so it can be blocked. */
    private PhishingReport reportFrom(UUID reportId, String sender) {
        return new PhishingReport(
                reportId, TENANT_ID, UUID.randomUUID(), "payload",
                null, 0.0, ReportStatus.SUBMITTED,
                null, null, sender, null, false, Instant.now());
    }

    @Test
    void confirmingWithBlacklistBlocksTheReportedSender() {
        UUID reportId = UUID.randomUUID();
        when(reportRepository.findById(reportId))
                .thenReturn(Optional.of(reportFrom(reportId, "kegian@lua-dao.vn")));
        when(reportRepository.save(any(PhishingReport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(blacklistRepository.existsByTenantIdAndTypeAndValue(
                TENANT_ID, BlacklistType.EMAIL, "kegian@lua-dao.vn")).thenReturn(false);

        reportingService.triage(reportId, TriageDecision.CONFIRM_THREAT, true);

        ArgumentCaptor<BlacklistEntry> entry = ArgumentCaptor.forClass(BlacklistEntry.class);
        verify(blacklistRepository).save(entry.capture());
        assertThat(entry.getValue().getType()).isEqualTo(BlacklistType.EMAIL);
        assertThat(entry.getValue().getValue()).isEqualTo("kegian@lua-dao.vn");
        assertThat(entry.getValue().getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void quarantiningWithBlacklistAlsoBlocksTheSender() {
        UUID reportId = UUID.randomUUID();
        when(reportRepository.findById(reportId))
                .thenReturn(Optional.of(reportFrom(reportId, "kegian@lua-dao.vn")));
        when(reportRepository.save(any(PhishingReport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(blacklistRepository.existsByTenantIdAndTypeAndValue(
                any(), any(), any())).thenReturn(false);

        reportingService.triage(reportId, TriageDecision.QUARANTINE, true);

        verify(blacklistRepository).save(any(BlacklistEntry.class));
    }

    @Test
    void reTriagingDoesNotAddTheSameSenderTwice() {
        UUID reportId = UUID.randomUUID();
        when(reportRepository.findById(reportId))
                .thenReturn(Optional.of(reportFrom(reportId, "kegian@lua-dao.vn")));
        when(reportRepository.save(any(PhishingReport.class))).thenAnswer(inv -> inv.getArgument(0));
        // Already on the list from an earlier triage of the same sender.
        when(blacklistRepository.existsByTenantIdAndTypeAndValue(
                TENANT_ID, BlacklistType.EMAIL, "kegian@lua-dao.vn")).thenReturn(true);

        reportingService.triage(reportId, TriageDecision.CONFIRM_THREAT, true);

        verify(blacklistRepository, never()).save(any(BlacklistEntry.class));
    }

    @Test
    void dismissingCannotAlsoBlockAndWritesNothingAtAll() {
        UUID reportId = UUID.randomUUID();
        when(reportRepository.findById(reportId))
                .thenReturn(Optional.of(reportFrom(reportId, "kegian@lua-dao.vn")));

        assertThatThrownBy(() ->
                reportingService.triage(reportId, TriageDecision.DISMISS, true))
                .isInstanceOf(IllegalArgumentException.class);

        // Rejected before any write: the report keeps its status too.
        verify(reportRepository, never()).save(any());
        verify(blacklistRepository, never()).save(any());
    }

    @Test
    void blockingIsRefusedWhenTheReportHasNoSenderToBlock() {
        UUID reportId = UUID.randomUUID();
        when(reportRepository.findById(reportId))
                .thenReturn(Optional.of(reportFrom(reportId, null)));

        // Better to refuse than to confirm the report and quietly block nothing.
        assertThatThrownBy(() ->
                reportingService.triage(reportId, TriageDecision.CONFIRM_THREAT, true))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reportRepository, never()).save(any());
        verify(blacklistRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void triageWhenReportNotFoundThrowsAndPublishesNothing() {
        // Arrange
        UUID missingId = UUID.randomUUID();
        when(reportRepository.findById(missingId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> reportingService.triage(missingId, TriageDecision.CONFIRM_THREAT, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(missingId.toString());

        verify(reportRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void triageWhenReportBelongsToAnotherTenantIsRejectedAndNothingIsWritten() {
        // Arrange: a report owned by a DIFFERENT tenant than the caller's context
        UUID reportId = UUID.randomUUID();
        UUID otherTenant = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PhishingReport foreignReport = new PhishingReport(
                reportId, otherTenant, UUID.randomUUID(), "payload",
                null, 0.0, ReportStatus.SUBMITTED);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(foreignReport));

        // Act + Assert: treated as not-found; no cross-tenant mutation or event
        assertThatThrownBy(() -> reportingService.triage(reportId, TriageDecision.CONFIRM_THREAT, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(reportId.toString());

        verify(reportRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
