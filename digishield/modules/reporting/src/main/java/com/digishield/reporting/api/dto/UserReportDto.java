package com.digishield.reporting.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * A learner's own phishing report, for the "My reports" screen. Deliberately
 * lighter than {@link PhishingReportDto}: no SOC-only fields (AI reasoning,
 * sender, reporter name), just what the reporter needs to track their report.
 *
 * @param id         report identifier
 * @param payload    the submitted content
 * @param channel    channel it came from (email | sms; may be null)
 * @param status     processing status (lowercase: submitted|triaging|confirmed|dismissed)
 * @param reportedAt when it was submitted
 * @param ageLabel   relative age label (e.g. "2p", "3h")
 */
public record UserReportDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("payload") String payload,
        @JsonProperty("channel") String channel,
        @JsonProperty("status") String status,
        @JsonProperty("reportedAt") Instant reportedAt,
        @JsonProperty("ageLabel") String ageLabel) {
}
