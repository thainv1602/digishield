package com.digishield.interception.web;

import com.digishield.interception.api.InterceptionService;
import com.digishield.interception.api.dto.AccountWatchEntryView;
import com.digishield.interception.api.dto.EvaluateRequest;
import com.digishield.interception.api.dto.InterventionDecision;
import com.digishield.interception.api.dto.InterventionEventView;
import com.digishield.interception.domain.AccountWatchEntry;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample REST controller for the Interception module.
 */
@RestController
public class InterceptionController {

    private final InterceptionService interceptionService;

    public InterceptionController(InterceptionService interceptionService) {
        this.interceptionService = interceptionService;
    }

    /**
     * Evaluates a transaction and returns an intervention decision (sample).
     */
    @PostMapping("/api/v1/interventions/evaluate")
    public InterventionDecision evaluate(@RequestBody EvaluateRequest request) {
        return interceptionService.evaluate(request);
    }

    /**
     * Intervention log for the SOC/reporting screens. Matches {@code GET /interventions}.
     *
     * @param page 1-based page number (default 1)
     * @param size page size (default 20, max 100)
     */
    @GetMapping("/api/v1/interventions")
    public List<InterventionEventView> listInterventions(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return interceptionService.listInterventions(page, size);
    }

    /**
     * Lists the tenant's watchlist of suspicious recipient accounts.
     * Matches {@code GET /account-watchlist}.
     */
    @GetMapping("/api/v1/account-watchlist")
    public List<AccountWatchEntryView> listWatchlist() {
        return interceptionService.listWatchlist();
    }

    /**
     * Adds/syncs a suspicious account into the watchlist (SIMO/NAPAS/A05).
     * Matches {@code POST /account-watchlist}.
     */
    @PostMapping("/api/v1/account-watchlist")
    public ResponseEntity<AccountWatchEntryView> addWatchEntry(@RequestBody AccountWatchEntryView request) {
        AccountWatchEntryView created = interceptionService.addWatchEntry(request);
        return ResponseEntity
                .created(URI.create("/api/v1/account-watchlist/" + created.id()))
                .body(created);
    }

    /**
     * Checks whether an identifier is in the watchlist (sample).
     */
    @GetMapping("/api/v1/account-watchlist/check")
    public CheckResponse check(@RequestParam("value") String value) {
        Optional<AccountWatchEntry> entry = interceptionService.checkAccount(value);
        return new CheckResponse(
                entry.isPresent(),
                entry.map(e -> e.getRiskLevel().name()).orElse(null));
    }

    /** DTO for watchlist check response. */
    public record CheckResponse(boolean inWatchlist, String riskLevel) {
    }
}
