package com.digishield.interception.web;

import com.digishield.interception.api.InterceptionService;
import com.digishield.interception.api.dto.EvaluateRequest;
import com.digishield.interception.api.dto.InterventionDecision;
import com.digishield.interception.domain.AccountWatchEntry;
import java.util.Optional;
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
