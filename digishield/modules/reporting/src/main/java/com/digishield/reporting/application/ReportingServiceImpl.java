package com.digishield.reporting.application;

import com.digishield.contracts.events.PhishingReportConfirmedEvent;
import com.digishield.reporting.api.ReportingService;
import com.digishield.reporting.api.dto.BlacklistEntryDto;
import com.digishield.reporting.api.dto.PhishingReportDto;
import com.digishield.reporting.domain.AiLabel;
import com.digishield.reporting.domain.BlacklistEntry;
import com.digishield.reporting.domain.BlacklistType;
import com.digishield.reporting.domain.PhishingReport;
import com.digishield.reporting.domain.ReportStatus;
import com.digishield.reporting.infrastructure.BlacklistEntryRepository;
import com.digishield.reporting.infrastructure.PhishingReportRepository;
import com.digishield.shared.messaging.EventPublisher;
import com.digishield.shared.tenantcontext.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link ReportingService}.
 */
@Service
@Transactional
public class ReportingServiceImpl implements ReportingService {

    private final PhishingReportRepository reportRepository;
    private final BlacklistEntryRepository blacklistRepository;
    private final EventPublisher eventPublisher;

    public ReportingServiceImpl(PhishingReportRepository reportRepository,
                                BlacklistEntryRepository blacklistRepository,
                                EventPublisher eventPublisher) {
        this.reportRepository = reportRepository;
        this.blacklistRepository = blacklistRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public PhishingReport submit(UUID userId, String payload) {
        UUID tenantId = TenantContext.requireUuid();
        PhishingReport report = new PhishingReport(
                UUID.randomUUID(), tenantId, userId, payload,
                null, 0.0, ReportStatus.SUBMITTED);
        return reportRepository.save(report);
    }

    @Override
    public PhishingReport triage(UUID reportId, boolean confirmThreat) {
        UUID tenantId = TenantContext.requireUuid();
        PhishingReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy báo cáo phishing: " + reportId));

        if (confirmThreat) {
            report.setAiLabel(AiLabel.THREAT);
            report.setStatus(ReportStatus.CONFIRMED);
            PhishingReport saved = reportRepository.save(report);
            eventPublisher.publish(new PhishingReportConfirmedEvent(tenantId, saved.getId()));
            return saved;
        }

        report.setAiLabel(AiLabel.CLEAN);
        report.setStatus(ReportStatus.DISMISSED);
        return reportRepository.save(report);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhishingReportDto> listReports(ReportStatus status) {
        UUID tenantId = TenantContext.requireUuid();
        List<PhishingReport> reports = status != null
                ? reportRepository.findByTenantIdAndStatusOrderByReportedAtDesc(tenantId, status)
                : reportRepository.findByTenantIdOrderByReportedAtDesc(tenantId);
        Instant now = Instant.now();
        return reports.stream().map(r -> toDto(r, now)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlacklistEntryDto> listBlacklist() {
        UUID tenantId = TenantContext.requireUuid();
        return blacklistRepository.findByTenantId(tenantId).stream()
                .map(this::toBlacklistDto)
                .toList();
    }

    @Override
    public BlacklistEntryDto addBlacklist(BlacklistType type, String value, String source) {
        UUID tenantId = TenantContext.requireUuid();
        BlacklistEntry entry = new BlacklistEntry(
                UUID.randomUUID(), tenantId, type, value, source);
        return toBlacklistDto(blacklistRepository.save(entry));
    }

    private PhishingReportDto toDto(PhishingReport r, Instant now) {
        return new PhishingReportDto(
                r.getId(),
                r.getUserId(),
                r.getReporter(),
                r.getSubject(),
                r.getSender(),
                r.getPayload(),
                r.getAiLabel() != null ? r.getAiLabel().name().toLowerCase() : null,
                r.getAiConfidence(),
                r.getAiReason(),
                r.isBlacklistMatch(),
                r.getStatus() != null ? r.getStatus().name().toLowerCase() : null,
                ageLabel(r.getReportedAt(), now));
    }

    private BlacklistEntryDto toBlacklistDto(BlacklistEntry e) {
        return new BlacklistEntryDto(
                e.getId(),
                e.getType() != null ? e.getType().name().toLowerCase() : null,
                e.getValue(),
                e.getSource());
    }

    /**
     * Builds a compact relative-age label (e.g. "2p", "3h", "1d"). The minute
     * suffix uses "p" (phút) to match the Vietnamese frontend.
     */
    private String ageLabel(Instant reportedAt, Instant now) {
        if (reportedAt == null) {
            return null;
        }
        Duration d = Duration.between(reportedAt, now);
        long mins = Math.max(0, d.toMinutes());
        if (mins < 60) {
            return mins + "p";
        }
        long hours = d.toHours();
        if (hours < 24) {
            return hours + "h";
        }
        return d.toDays() + "d";
    }
}
