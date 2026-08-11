package com.digishield.reporting.application;

import com.digishield.reporting.api.dto.BlacklistEntryDto;
import com.digishield.reporting.api.dto.UserReportDto;
import com.digishield.reporting.domain.BlacklistEntry;
import com.digishield.reporting.domain.BlacklistType;
import com.digishield.reporting.domain.PhishingReport;
import com.digishield.reporting.domain.ReportStatus;
import com.digishield.reporting.infrastructure.BlacklistEntryRepository;
import com.digishield.reporting.infrastructure.PhishingReportRepository;
import com.digishield.reporting.infrastructure.ThreatIntelRepository;
import com.digishield.shared.messaging.EventPublisher;
import com.digishield.shared.tenantcontext.AuditRecorder;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a learner and an analyst read back: "my reports", the blacklist, and
 * turning a real attack into training material.
 */
@ExtendWith(MockitoExtension.class)
class ReportingQueriesTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private PhishingReportRepository reportRepository;

    @Mock
    private BlacklistEntryRepository blacklistRepository;

    @Mock
    private ThreatIntelRepository threatIntelRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ObjectProvider<AuditRecorder> auditRecorder;

    @InjectMocks
    private ReportingServiceImpl service;

    @BeforeEach
    void setTenant() {
        TenantContext.set(TENANT.toString());
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private PhishingReport reportedAt(Instant when) {
        return new PhishingReport(UUID.randomUUID(), TENANT, UUID.randomUUID(), "payload",
                null, 0.0, ReportStatus.SUBMITTED, null, null, "kegian@lua-dao.vn",
                null, false, when);
    }

    @Test
    @DisplayName("age is shown in the largest unit that still fits")
    void ageLabelChangesUnitAsItGrows() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        when(reportRepository.findByTenantIdAndUserIdOrderByReportedAtDesc(TENANT, userId))
                .thenReturn(List.of(
                        reportedAt(now.minus(Duration.ofMinutes(5))),
                        reportedAt(now.minus(Duration.ofHours(3))),
                        reportedAt(now.minus(Duration.ofDays(2)))));

        List<UserReportDto> reports = service.listUserReports(userId);

        assertThat(reports).extracting(UserReportDto::ageLabel)
                .containsExactly("5p", "3h", "2d");
    }

    @Test
    @DisplayName("a report from the future reads as new, not as a negative age")
    void clockSkewDoesNotProduceANegativeAge() {
        UUID userId = UUID.randomUUID();
        // A recipient's clock can run ahead; "-3p ago" would be worse than "0p".
        when(reportRepository.findByTenantIdAndUserIdOrderByReportedAtDesc(TENANT, userId))
                .thenReturn(List.of(reportedAt(Instant.now().plus(Duration.ofMinutes(3)))));

        assertThat(service.listUserReports(userId).getFirst().ageLabel()).isEqualTo("0p");
    }

    @Test
    void aReportWithNoTimestampHasNoAgeLabel() {
        UUID userId = UUID.randomUUID();
        when(reportRepository.findByTenantIdAndUserIdOrderByReportedAtDesc(TENANT, userId))
                .thenReturn(List.of(reportedAt(null)));

        assertThat(service.listUserReports(userId).getFirst().ageLabel()).isNull();
    }

    @Test
    @DisplayName("the status a learner sees is lower case, matching the API contract")
    void statusIsLowerCasedForTheWire() {
        UUID userId = UUID.randomUUID();
        when(reportRepository.findByTenantIdAndUserIdOrderByReportedAtDesc(TENANT, userId))
                .thenReturn(List.of(reportedAt(Instant.now())));

        assertThat(service.listUserReports(userId).getFirst().status()).isEqualTo("submitted");
    }

    @Test
    @DisplayName("blacklisting records the tenant, the type and an audit entry")
    void addingToTheBlacklistIsAudited() {
        when(blacklistRepository.save(any(BlacklistEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BlacklistEntryDto added = service.addBlacklist(
                BlacklistType.EMAIL, "kegian@lua-dao.vn", "triage");

        assertThat(added.type()).isEqualTo("email");
        assertThat(added.value()).isEqualTo("kegian@lua-dao.vn");
        assertThat(added.source()).isEqualTo("triage");
    }

    @Test
    @DisplayName("converting a report to training marks the report, once")
    void convertingMarksTheReport() {
        UUID reportId = UUID.randomUUID();
        PhishingReport report = reportedAt(Instant.now());
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        service.convertReportToTraining(reportId);

        assertThat(report.isConvertedToTraining()).isTrue();
        verify(reportRepository).save(report);
    }

    @Test
    @DisplayName("a report of another tenant cannot be converted")
    void convertingAcrossTenantsIsRefused() {
        UUID reportId = UUID.randomUUID();
        PhishingReport foreign = new PhishingReport(reportId, OTHER_TENANT, UUID.randomUUID(),
                "payload", null, 0.0, ReportStatus.SUBMITTED);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.convertReportToTraining(reportId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reportRepository, never()).save(any());
    }
}
