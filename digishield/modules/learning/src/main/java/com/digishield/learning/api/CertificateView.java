package com.digishield.learning.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Certificate detail for the Certificate screen.
 *
 * @param id          certificate identifier
 * @param serial      verification serial number
 * @param courseTitle title of the completed course
 * @param recipient   recipient full name
 * @param score       achieved score (out of 100, may be null)
 * @param issuedAt    issue timestamp
 * @param validUntil  expiry timestamp (may be null)
 * @param verifyUrl   public verification URL
 * @param qr          QR payload / verification link encoded in the QR code
 */
public record CertificateView(UUID id, String serial, String courseTitle, String recipient,
                              Integer score, Instant issuedAt, Instant validUntil,
                              String verifyUrl, String qr) {
}
