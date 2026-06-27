package com.digishield.reporting.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Phishing report view for the SOC inbox table and detail drawer.
 * <p>
 * Enum values are emitted as lowercase strings to match the frontend
 * ({@code aiLabel} of clean|spam|threat, {@code status}).
 *
 * @param id             report identifier
 * @param userId         the reporting user's identifier
 * @param reporter       the reporting user's display name
 * @param subject        subject line of the reported message
 * @param sender         sender address of the reported message
 * @param payload        sanitized raw content
 * @param aiLabel        AI classification (clean|spam|threat)
 * @param aiConfidence   AI confidence in the range 0..1
 * @param reasoning      AI reasoning for the classification
 * @param blacklistMatch whether the message matched a blacklist source
 * @param status         processing status (lowercase)
 * @param ageLabel       relative age label (e.g. "2p", "8p")
 */
public record PhishingReportDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("userId") UUID userId,
        @JsonProperty("reporter") String reporter,
        @JsonProperty("subject") String subject,
        @JsonProperty("sender") String sender,
        @JsonProperty("payload") String payload,
        @JsonProperty("aiLabel") String aiLabel,
        @JsonProperty("aiConfidence") double aiConfidence,
        @JsonProperty("reasoning") String reasoning,
        @JsonProperty("blacklistMatch") boolean blacklistMatch,
        @JsonProperty("status") String status,
        @JsonProperty("ageLabel") String ageLabel) {
}
