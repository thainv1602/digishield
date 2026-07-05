package com.digishield.reporting.web;

import com.digishield.reporting.api.ReportingService;
import com.digishield.reporting.api.dto.BlacklistEntryDto;
import com.digishield.reporting.domain.BlacklistType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * REST controller for the blacklist / watchlist screen. Matches
 * {@code GET /blacklist} and {@code POST /blacklist}.
 */
@RestController
@RequestMapping("/api/v1/blacklist")
@PreAuthorize("hasRole('ANALYST')")
public class BlacklistController {

    private final ReportingService reportingService;

    public BlacklistController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping
    public ResponseEntity<List<BlacklistEntryDto>> list() {
        return ResponseEntity.ok(reportingService.listBlacklist());
    }

    @PostMapping
    public ResponseEntity<BlacklistEntryDto> add(@RequestBody AddBlacklistRequest request) {
        BlacklistType type = BlacklistType.valueOf(request.type().trim().toUpperCase());
        BlacklistEntryDto created = reportingService.addBlacklist(type, request.value(), request.source());
        return ResponseEntity
                .created(URI.create("/api/v1/blacklist/" + created.id()))
                .body(created);
    }

    /**
     * Add-blacklist payload.
     *
     * @param type   entry type (e.g. "url", "phone", "domain")
     * @param value  the value to blacklist
     * @param source the source (e.g. "NCSC")
     */
    public record AddBlacklistRequest(String type, String value, String source) {
    }
}
