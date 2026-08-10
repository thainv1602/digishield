package com.digishield.reporting.web;

import com.digishield.reporting.api.ReportingService;
import com.digishield.reporting.api.TriageDecision;
import com.digishield.reporting.api.dto.PhishingReportDto;
import com.digishield.reporting.api.dto.UserReportDto;
import com.digishield.reporting.domain.ReportStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    public ResponseEntity<PhishingReportDto> submit(@RequestBody SubmitReportRequest request) {
        PhishingReportDto report = reportingService.submit(
                request.userId(), request.payload(), request.channel());
        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    /**
     * A learner's own submitted reports ("My reports"). Matches
     * {@code GET /reports/phishing/mine/{userId}}.
     *
     * @param userId the reporting learner
     */
    @PreAuthorize("hasRole('LEARNER')")
    @GetMapping("/phishing/mine/{userId}")
    public ResponseEntity<List<UserReportDto>> myReports(@PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(reportingService.listUserReports(userId));
    }

    /**
     * Records an analyst's verdict on a report. {@code decision} is one of
     * {@code confirm_threat}, {@code quarantine} or {@code dismiss}.
     *
     * @param id      the report being triaged
     * @param request the decision
     */
    @PreAuthorize("hasRole('ANALYST')")
    @PostMapping("/phishing/{id}/triage")
    public ResponseEntity<PhishingReportDto> triage(@PathVariable("id") UUID id,
                                                    @RequestBody TriageRequest request) {
        PhishingReportDto report = reportingService.triage(
                id, request.toDecision(), request.blocksSender());
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
    public record SubmitReportRequest(UUID userId, String payload, String channel) {
    }

    /**
     * A rejected triage is the caller's error, not a server fault. Without this
     * the service's IllegalArgumentException left Spring to answer 500 -- for a
     * mistyped report id as much as for a contradictory request.
     *
     * @param e the rejection
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> handleRejected(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /**
     * Triage request body: {@code {"decision": "confirm_threat"}}.
     *
     * <p>An unknown value is rejected rather than silently treated as a
     * dismissal — the previous boolean had no way to say "I do not understand
     * this", so anything that was not "confirm" cleared the report.
     */
    public record TriageRequest(
            String decision,
            @JsonProperty("add_to_blacklist") Boolean addToBlacklist) {

        private static final String ALLOWED = "confirm_threat | quarantine | dismiss";

        boolean blocksSender() {
            return Boolean.TRUE.equals(addToBlacklist);
        }

        TriageDecision toDecision() {
            // There is no @ControllerAdvice in this application, so an
            // IllegalArgumentException would surface as 500. A malformed body is
            // the caller's error: say 400 explicitly.
            if (decision == null || decision.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Thiếu trường decision (" + ALLOWED + ")");
            }
            try {
                return TriageDecision.valueOf(decision.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Quyết định phân loại không hợp lệ: " + decision
                                + " (hợp lệ: " + ALLOWED + ")");
            }
        }
    }
}
