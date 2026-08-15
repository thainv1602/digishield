package com.digishield.interception.api.dto;

import java.util.List;

/**
 * Result of an intervention decision.
 *
 * <p>Both the decision and the signals are lower case on the wire, matching the
 * OpenAPI schemas and what {@code GET /interventions} returns for the same
 * event. Anything counting signals across the two endpoints has one population
 * to count, not two spellings of it.
 *
 * @param decision the decision: {@code allow}, {@code warn}, {@code pause} or
 *                 {@code block}
 * @param signals  the signals that fired, e.g. {@code ["on_call", "new_payee"]}
 *                 — see {@code InterventionSignal}
 * @param message  educational message displayed to the user
 */
public record InterventionDecision(String decision, List<String> signals, String message) {
}
