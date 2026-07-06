package com.digishield.reporting.web;

import com.digishield.reporting.api.ReportingService;
import com.digishield.reporting.api.dto.ThreatIntelConvertResultDto;
import com.digishield.reporting.api.dto.ThreatIntelDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * REST controller for the ThreatFlip threat-intel screen. Matches
 * {@code GET /threat-intel}, {@code POST /threat-intel} and
 * {@code POST /threat-intel/{id}/convert}.
 */
@RestController
@RequestMapping("/api/v1/threat-intel")
@PreAuthorize("hasRole('ANALYST')")
public class ThreatIntelController {

    private final ReportingService reportingService;

    public ThreatIntelController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping
    public ResponseEntity<List<ThreatIntelDto>> list() {
        return ResponseEntity.ok(reportingService.listThreatIntel());
    }

    @PostMapping
    public ResponseEntity<ThreatIntelDto> ingest(@RequestBody ThreatIntelDto request) {
        ThreatIntelDto created = reportingService.ingestThreatIntel(
                request.source(), request.rawPayload());
        return ResponseEntity
                .created(URI.create("/api/v1/threat-intel/" + created.id()))
                .body(created);
    }

    @PostMapping("/{id}/convert")
    public ResponseEntity<ThreatIntelConvertResultDto> convert(@PathVariable("id") UUID id) {
        ThreatIntelConvertResultDto result = reportingService.convertThreatIntel(id);
        return ResponseEntity
                .created(URI.create("/api/v1/threat-intel/" + id + "/convert"))
                .body(result);
    }
}
