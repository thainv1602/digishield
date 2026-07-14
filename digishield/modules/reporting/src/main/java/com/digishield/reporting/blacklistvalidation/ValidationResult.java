package com.digishield.reporting.blacklistvalidation;

/**
 * Result of validating and normalizing a blacklist value.
 *
 * @param valid      whether the value is well-formed for its type
 * @param normalized the canonical form to store (e.g. lowercased domain,
 *                   E.164 phone); equals the trimmed input when invalid
 * @param reason     machine-readable reason when {@code valid == false}
 *                   (e.g. {@code "empty"}, {@code "bad_format"}), or
 *                   {@code "ok"} when valid
 */
public record ValidationResult(boolean valid, String normalized, String reason) {

    static ValidationResult ok(String normalized) {
        return new ValidationResult(true, normalized, "ok");
    }

    static ValidationResult invalid(String original, String reason) {
        return new ValidationResult(false, original, reason);
    }
}
