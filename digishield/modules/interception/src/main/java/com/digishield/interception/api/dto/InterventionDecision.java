package com.digishield.interception.api.dto;

import java.util.List;

/**
 * Result of an intervention decision.
 *
 * @param decision the decision (ALLOW/WARN/PAUSE/BLOCK)
 * @param signals  list of detected signals
 * @param message  educational message displayed to the user
 */
public record InterventionDecision(String decision, List<String> signals, String message) {
}
