package com.digishield.learning.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire view of a {@code Certificate} list item (OpenAPI {@code Certificate}
 * schema, returned by {@code GET /users/{id}/certificates}). Serializes with
 * snake_case field names.
 *
 * @param id       certificate identifier
 * @param userId   recipient user id
 * @param courseId completed course id
 * @param serial   verification serial number
 * @param pdfRef   reference to the rendered PDF (verification URL/asset key)
 * @param issuedAt issue timestamp
 */
public record UserCertificateView(
        @JsonProperty("id") UUID id,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("course_id") UUID courseId,
        @JsonProperty("serial") String serial,
        @JsonProperty("pdf_ref") String pdfRef,
        @JsonProperty("issued_at") Instant issuedAt) {
}
