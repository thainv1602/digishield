package com.digishield.reporting.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Blacklist / watchlist entry view, matching the OpenAPI {@code BlacklistEntry}
 * schema ({@code type} lowercased to match the frontend).
 *
 * @param id     entry identifier
 * @param type   entry type (lowercase, e.g. "url", "phone", "domain")
 * @param value  the blacklisted value
 * @param source the source (e.g. "NCSC")
 */
public record BlacklistEntryDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("type") String type,
        @JsonProperty("value") String value,
        @JsonProperty("source") String source) {
}
