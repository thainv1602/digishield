package com.digishield.reporting.api;

import com.digishield.reporting.api.dto.BlacklistEntryDto;
import com.digishield.reporting.api.dto.PhishingReportDto;
import com.digishield.reporting.api.dto.ThreatIntelConvertResultDto;
import com.digishield.reporting.api.dto.ThreatIntelDto;
import com.digishield.reporting.domain.BlacklistType;
import com.digishield.reporting.domain.PhishingReport;
import com.digishield.reporting.domain.ReportStatus;

import java.util.List;
import java.util.UUID;

/**
 * Public API of the reporting module.
 */
public interface ReportingService {

    /**
     * A user submits a new phishing report.
     *
     * @param userId  the user submitting the report
     * @param payload the raw content of the suspicious email/message
     * @return the newly created report (status SUBMITTED)
     */
    PhishingReport submit(UUID userId, String payload);

    /**
     * Triages a report.
     * <p>
     * If {@code confirmThreat == true}, the report moves to CONFIRMED and a
     * {@code PhishingReportConfirmedEvent} is emitted.
     *
     * @param reportId      the report to triage
     * @param confirmThreat whether this is confirmed as a threat
     * @return the report after triage
     */
    PhishingReport triage(UUID reportId, boolean confirmThreat);

    /**
     * Lists phishing reports for the SOC inbox, optionally filtered by status.
     *
     * @param status optional status filter (null = all)
     * @return report views (newest first)
     */
    List<PhishingReportDto> listReports(ReportStatus status);

    /**
     * Lists blacklist entries for the current tenant.
     *
     * @return blacklist entry views
     */
    List<BlacklistEntryDto> listBlacklist();

    /**
     * Adds a blacklist entry for the current tenant.
     *
     * @param type   the entry type
     * @param value  the value to blacklist
     * @param source the source (e.g. "NCSC")
     * @return the created entry view
     */
    BlacklistEntryDto addBlacklist(BlacklistType type, String value, String source);

    /**
     * Lists threat-intel records for the current tenant (newest first).
     *
     * @return threat-intel views
     */
    List<ThreatIntelDto> listThreatIntel();

    /**
     * Ingests a new threat-intel record from a feed/NCSC for the current tenant.
     *
     * @param source     origin of the intel (e.g. "NCSC")
     * @param rawPayload raw payload of the threat sample
     * @return the created threat-intel view
     */
    ThreatIntelDto ingestThreatIntel(String source, String rawPayload);

    /**
     * Converts a real threat into a de-identified template/coaching draft and
     * marks the intel record as converted.
     *
     * @param threatIntelId the intel to convert
     * @return identifiers of the generated template and coaching page drafts
     */
    ThreatIntelConvertResultDto convertThreatIntel(UUID threatIntelId);

    /**
     * Flags a phishing report as flipped into training content.
     *
     * @param reportId the report to convert
     */
    void convertReportToTraining(UUID reportId);
}
