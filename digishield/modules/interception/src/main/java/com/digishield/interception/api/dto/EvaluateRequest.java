package com.digishield.interception.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to evaluate a transaction for an intervention decision.
 *
 * @param userId      the user performing the transaction
 * @param amount      the amount
 * @param destAccount the recipient account/identifier
 * @param onCall      whether the user is currently on a phone call
 * @param newPayee    first-time recipient (never transferred before)
 */
public record EvaluateRequest(UUID userId, BigDecimal amount, String destAccount, boolean onCall, boolean newPayee) {
}
