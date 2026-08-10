package com.digishield.reporting.application;

import com.digishield.contracts.events.PhishingReportConfirmedEvent;
import com.digishield.contracts.events.ThreatIntelConvertedEvent;
import com.digishield.reporting.api.ReportingService;
import com.digishield.reporting.api.TriageDecision;
import com.digishield.reporting.api.dto.BlacklistEntryDto;
import com.digishield.reporting.api.dto.PhishingReportDto;
import com.digishield.reporting.api.dto.ThreatIntelConvertResultDto;
import com.digishield.reporting.api.dto.ThreatIntelDto;
import com.digishield.reporting.api.dto.UserReportDto;
import com.digishield.reporting.domain.AiLabel;
import com.digishield.reporting.domain.BlacklistEntry;
import com.digishield.reporting.domain.BlacklistType;
import com.digishield.reporting.domain.PhishingReport;
import com.digishield.reporting.domain.ReportStatus;
import com.digishield.reporting.domain.ThreatIntel;
import com.digishield.reporting.infrastructure.BlacklistEntryRepository;
import com.digishield.reporting.infrastructure.PhishingReportRepository;
import com.digishield.reporting.infrastructure.ThreatIntelRepository;
import com.digishield.shared.messaging.EventPublisher;
import com.digishield.shared.tenantcontext.AuditRecorder;
import com.digishield.shared.tenantcontext.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ThreatIntelRepository threatIntelRepository;
    private final EventPublisher eventPublisher;
    /** Optional: absent in slices that do not wire the application shell. */
    private final ObjectProvider<AuditRecorder> auditRecorder;

    public ReportingServiceImpl(PhishingReportRepository reportRepository,
                                BlacklistEntryRepository blacklistRepository,
                                ThreatIntelRepository threatIntelRepository,
                                EventPublisher eventPublisher,
                                ObjectProvider<AuditRecorder> auditRecorder) {
        this.reportRepository = reportRepository;
        this.blacklistRepository = blacklistRepository;
        this.threatIntelRepository = threatIntelRepository;
        this.eventPublisher = eventPublisher;
        this.auditRecorder = auditRecorder;
    }

    /** Records an auditable action, if an audit sink is wired (absent in slices). */
    private void audit(String action, String target, AuditRecorder.Severity severity) {
        AuditRecorder recorder = auditRecorder.getIfAvailable();
        if (recorder != null) {
            recorder.record(action, target, severity);
        }
    }

    @Override
    public PhishingReportDto submit(UUID userId, String payload, String channel) {
        UUID tenantId = TenantContext.requireUuid();
        PhishingReport report = new PhishingReport(
                UUID.randomUUID(), tenantId, userId, payload,
                null, 0.0, ReportStatus.SUBMITTED);
        report.setChannel(channel);
        return toDto(reportRepository.save(report), Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserReportDto> listUserReports(UUID userId) {
        UUID tenantId = TenantContext.requireUuid();
        Instant now = Instant.now();
        return reportRepository
                .findByTenantIdAndUserIdOrderByReportedAtDesc(tenantId, userId)
                .stream()
                .map(r -> new UserReportDto(
                        r.getId(),
                        r.getPayload(),
                        r.getChannel(),
                        r.getStatus() != null ? r.getStatus().name().toLowerCase() : null,
                        r.getReportedAt(),
                        ageLabel(r.getReportedAt(), now)))
                .toList();
    }

    @Override
    public PhishingReportDto triage(UUID reportId, TriageDecision decision) {
        UUID tenantId = TenantContext.requireUuid();
        PhishingReport report = reportRepository.findById(reportId)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy báo cáo phishing: " + reportId));

        switch (decision) {
            case CONFIRM_THREAT -> {
                report.setAiLabel(AiLabel.THREAT);
                report.setStatus(ReportStatus.CONFIRMED);
                PhishingReport saved = reportRepository.save(report);
                audit("triage.confirm", "report:" + reportId, AuditRecorder.Severity.SENSITIVE);
                eventPublisher.publish(
                        new PhishingReportConfirmedEvent(
                                tenantId, saved.getUserId(), saved.getId()));
                return toDto(saved, Instant.now());
            }
            case QUARANTINE -> {
                // Labelled a threat so it reads as one everywhere, but no event:
                // the reporter is rewarded when the verdict is final, not before.
                report.setAiLabel(AiLabel.THREAT);
                report.setStatus(ReportStatus.QUARANTINED);
                audit("triage.quarantine", "report:" + reportId,
                        AuditRecorder.Severity.SENSITIVE);
                return toDto(reportRepository.save(report), Instant.now());
            }
            case DISMISS -> {
                report.setAiLabel(AiLabel.CLEAN);
                report.setStatus(ReportStatus.DISMISSED);
                audit("triage.dismiss", "report:" + reportId, AuditRecorder.Severity.SENSITIVE);
                return toDto(reportRepository.save(report), Instant.now());
            }
            default -> throw new IllegalArgumentException(
                    "Quyết định phân loại không hợp lệ: " + decision);
        }
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
        audit("blacklist.add", type.name().toLowerCase() + ":" + value,
                AuditRecorder.Severity.SENSITIVE);
        return toBlacklistDto(blacklistRepository.save(entry));
    }

    @Override
    public void deleteBlacklist(UUID id) {
        // Tenant must be set; the RLS-scoped findById only sees this tenant's rows.
        TenantContext.requireUuid();
        blacklistRepository.findById(id).ifPresent(blacklistRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ThreatIntelDto> listThreatIntel() {
        UUID tenantId = TenantContext.requireUuid();
        return threatIntelRepository.findByTenantIdOrderByCollectedAtDesc(tenantId).stream()
                .map(this::toThreatIntelDto)
                .toList();
    }

    @Override
    public ThreatIntelDto ingestThreatIntel(String source, String rawPayload) {
        UUID tenantId = TenantContext.requireUuid();
        ThreatIntel intel = new ThreatIntel(
                UUID.randomUUID(), tenantId, source, rawPayload, null, Instant.now());
        return toThreatIntelDto(threatIntelRepository.save(intel));
    }

    @Override
    public ThreatIntelConvertResultDto convertThreatIntel(UUID threatIntelId) {
        UUID tenantId = TenantContext.requireUuid();
        ThreatIntel intel = threatIntelRepository.findById(threatIntelId)
                .filter(t -> tenantId.equals(t.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy threat intel: " + threatIntelId));

        // Mint the de-identified template id + coaching page id, mark the intel
        // converted, then ask the learning module (via event) to create the
        // actual coaching page with that id — so the returned coachingPageId
        // references real content instead of a dangling identifier.
        UUID templateId = intel.getConvertedTemplateId() != null
                ? intel.getConvertedTemplateId()
                : UUID.randomUUID();
        UUID coachingPageId = UUID.randomUUID();
        intel.setConvertedTemplateId(templateId);
        threatIntelRepository.save(intel);

        String contentRef = "threat-intel:" + (intel.getSource() != null ? intel.getSource() : intel.getId());
        eventPublisher.publish(new ThreatIntelConvertedEvent(tenantId, coachingPageId, templateId, contentRef));

        return new ThreatIntelConvertResultDto(templateId, coachingPageId);
    }

    @Override
    public void convertReportToTraining(UUID reportId) {
        UUID tenantId = TenantContext.requireUuid();
        PhishingReport report = reportRepository.findById(reportId)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy báo cáo phishing: " + reportId));
        report.setConvertedToTraining(true);
        reportRepository.save(report);
    }

    private ThreatIntelDto toThreatIntelDto(ThreatIntel t) {
        return new ThreatIntelDto(
                t.getId(),
                t.getSource(),
                t.getRawPayload(),
                t.getConvertedTemplateId(),
                t.getCollectedAt());
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
                r.getChannel(),
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
