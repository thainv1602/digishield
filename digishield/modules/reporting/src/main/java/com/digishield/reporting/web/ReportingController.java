package com.digishield.reporting.web;

import com.digishield.reporting.api.ReportingService;
import com.digishield.reporting.api.dto.PhishingReportDto;
import com.digishield.reporting.domain.PhishingReport;
import com.digishield.reporting.domain.ReportStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * REST controller for the reporting module.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    /**
     * SOC inbox queue. Matches {@code GET /reports/phishing?status=}.
     *
     * @param status optional status filter (e.g. "confirmed")
     */
    @PreAuthorize("hasRole('ANALYST')")
    @GetMapping("/phishing")
    public ResponseEntity<List<PhishingReportDto>> list(
            @RequestParam(value = "status", required = false) String status) {
        ReportStatus parsed = status != null && !status.isBlank()
                ? ReportStatus.valueOf(status.trim().toUpperCase())
                : null;
        return ResponseEntity.ok(reportingService.listReports(parsed));
    }

    @PreAuthorize("hasRole('LEARNER')")
    @PostMapping("/phishing")
    public ResponseEntity<PhishingReport> submit(@RequestBody SubmitReportRequest request) {
        PhishingReport report = reportingService.submit(request.userId(), request.payload());
        return ResponseEntity.ok(report);
    }

    @PreAuthorize("hasRole('ANALYST')")
    @PostMapping("/phishing/{id}/triage")
    public ResponseEntity<PhishingReport> triage(@PathVariable("id") UUID id,
                                                 @RequestBody TriageRequest request) {
        PhishingReport report = reportingService.triage(id, request.confirmThreat());
        return ResponseEntity.ok(report);
    }

    /**
     * Flips a reported email into training content. Matches
     * {@code POST /reports/phishing/{id}/convert-to-training}.
     *
     * @param id the report to convert
     */
    @PreAuthorize("hasRole('ANALYST')")
    @PostMapping("/phishing/{id}/convert-to-training")
    public ResponseEntity<Void> convertToTraining(@PathVariable("id") UUID id) {
        reportingService.convertReportToTraining(id);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Report submission payload.
     */
    public record SubmitReportRequest(UUID userId, String payload) {
    }

    /**
     * Triage payload.
     */
    public record TriageRequest(boolean confirmThreat) {
    }
}
