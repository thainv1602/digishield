package com.digishield.interception.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Watchlist entry view, matching the OpenAPI {@code AccountWatchEntry} schema.
 * <p>
 * Wire field names are snake_case ({@code risk_level}, {@code added_at}) and enum
 * values are emitted as lowercase strings to match the frontend
 * ({@code type} of bank_account|phone|wallet, {@code risk_level} of watch|high|confirmed).
 * <p>
 * This record is used both as the response item and as the {@code POST} request body.
 * On create, {@code id} and {@code added_at} may be omitted and are assigned by the server.
 *
 * @param id        entry identifier (server-assigned on create)
 * @param type      identifier type (bank_account|phone|wallet)
 * @param value     the suspicious account/phone/wallet value
 * @param riskLevel risk level (watch|high|confirmed)
 * @param source    feed source (e.g. SIMO, NAPAS, A05)
 * @param addedAt   when the entry was added/synced (server-assigned on create)
 */
public record AccountWatchEntryView(
        @JsonProperty("id") UUID id,
        @JsonProperty("type") String type,
        @JsonProperty("value") String value,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("source") String source,
        @JsonProperty("added_at") Instant addedAt) {
}
